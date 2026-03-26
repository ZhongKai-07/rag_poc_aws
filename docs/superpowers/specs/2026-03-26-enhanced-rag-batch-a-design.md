# Enhanced RAG Batch A Design — Query Rewriting, Citation, Offline Evaluation

**Date:** 2026-03-26
**Status:** Approved
**Scope:** Batch A (core quality improvements) of Phase 1 Enhanced RAG

## 1. Background & Goals

The current RAG pipeline is a linear fixed flow: `Query → Embed → KNN/BM25 → Rerank → LLM Generate`. It lacks query understanding, answer traceability, and measurable quality metrics.

**Batch A delivers three capabilities:**

1. **Query Rewriting** — understand and transform user queries for better retrieval
2. **Answer Citation** — trace answers back to source documents with inline references
3. **Offline Evaluation** — measure and iterate on RAG quality using standardized metrics

**Business context:**

- Scene 1 (COB Compliance): ~100 queries/day, natural language questions about AML/KYC regulations, PI classification, World-Check screening
- Scene 2 (Collateral): ~10-20 queries/day, structured parameter lookups in ISDA/CSA/GMRA agreements — `[counterparty] + [agreement_type] + [business_field]`

**Non-goals for Batch A:**

- Multi-turn conversation (Batch B)
- Streaming output (Batch B)
- User feedback mechanism (Batch B)
- Langfuse / online observability (Batch B)
- Spring AI framework migration (Batch B)
- Agent / tool-use capabilities (Phase 2)

## 2. Architecture Approach

**Approach chosen: Pipeline-inline enhancement** — insert new steps into the existing `RagQueryApplicationService` pipeline without changing the hexagonal architecture or introducing new frameworks.

Alternatives considered and rejected:

- *Preprocessing pipeline separation* — over-engineered for POC stage
- *LangChain4j framework integration* — conflicts with existing architecture, adds unnecessary dependency
- *Spring AI migration* — deferred to Batch B where its value (ChatMemory, streaming) is higher

## 3. Query Rewriting Module

### 3.1 Design Pattern: Strategy + Registry

Different business scenes require fundamentally different query understanding logic. A Strategy pattern with auto-registration via Spring container provides open/closed extensibility.

```
User Query
  → QueryRewriteRouter (application layer)
      → selects strategy via supports(module)
      → [COB] CobKeywordRewriteStrategy
      → [Collateral] CollateralStructuredRewriteStrategy
  → RewriteResult
  → into Retrieve → Rerank → Generate pipeline
```

### 3.2 Domain Layer

```java
// domain/rag/QueryRewriteStrategy.java
public interface QueryRewriteStrategy {
    boolean supports(String module);
    RewriteResult rewrite(String query);
}

// domain/rag/RewriteResult.java
public record RewriteResult(
    String rewrittenQuery,
    List<String> keywords,
    StructuredQuery structured,  // nullable, Collateral only
    String originalQuery
) {}

// domain/rag/StructuredQuery.java
public record StructuredQuery(
    String counterparty,
    String agreementType,
    String businessField,
    String fallbackQuery
) {}
```

### 3.3 Application Layer

```java
// application/rag/QueryRewriteRouter.java
public class QueryRewriteRouter {
    private final List<QueryRewriteStrategy> strategies;
    private final QueryRewriteStrategy defaultStrategy;  // COB as default

    public RewriteResult rewrite(String query, String module) {
        return strategies.stream()
            .filter(s -> s.supports(module))
            .findFirst()
            .orElse(defaultStrategy)
            .rewrite(query);
    }
}
```

### 3.4 COB Strategy: Keyword Extraction + Rewrite

**Input:** User query in natural language
**Output:** Rewritten query + extracted business domain keywords

Prompt template:

```
你是一个证券公司合规领域的查询理解专家。请分析用户问题，完成两个任务：
1. 提取核心业务领域关键词（如AML、KYC、World-Check、name screening、PI classification等）
2. 将问题改写为适合文档检索的形式（去口语化、补全缩写、明确指代）

用户问题：{query}

输出JSON格式：
{"rewritten_query": "...", "keywords": ["...", "..."]}
```

**Retrieval strategy:** `rewritten_query` for vector search, `keywords` for BM25 text search (MIX mode).

### 3.5 Collateral Strategy: Structured Triple Parsing

**Input:** User query about agreement parameters
**Output:** Structured triple `{counterparty, agreementType, businessField}` + fallback query

Prompt template:

```
你是一个证券公司Collateral业务专家。请从用户问题中提取以下结构化信息：
1. counterparty：交易对手方名称（如HSBC、Goldman Sachs）
2. agreement_type：协议类型（如ISDA、CSA、GMRA、GMSLA）
3. business_field：查询的具体业务字段（如minimum transfer amount、initial margin、eligible collateral）
4. fallback_query：如果无法完全结构化，保留原始问题的检索友好改写

用户问题：{query}

输出JSON格式：
{"counterparty": "...", "agreement_type": "...", "business_field": "...", "fallback_query": "..."}
```

**Retrieval strategy:**
1. Metadata filter by `counterparty` + `agreement_type` to narrow OpenSearch scope
2. Semantic search by `business_field` to locate specific field value
3. If structured parsing fails (null fields), degrade to `fallback_query` standard retrieval

### 3.6 Collateral Metadata Acquisition

Agreement metadata (counterparty, agreement type) is required for effective filtering. Two-source approach:

1. **Directory structure convention:** Upload files organized as `{counterparty}/{agreement_type}/` (e.g., `HSBC/ISDA_CSA/`). System extracts metadata from path during ingestion.
2. **LLM extraction at parse time:** During BDA/Docling parsing, extract counterparty, agreement type, and signing date from first page as supplementary/validation metadata.

Metadata is stored in OpenSearch document `metadata` fields and used for pre-filtering during Collateral retrieval.

### 3.7 Pipeline Integration Point

In `RagQueryApplicationService.handle()`, insert before retrieval:

```
Before: query → retrieve(query) → rerank → generate
After:  query → queryRewriteRouter.rewrite(query, module) → retrieve(rewriteResult) → rerank → generate
```

`RetrievalPort` interface extended to accept optional metadata filter parameters for Collateral structured queries.

### 3.8 Model Selection for Rewriting

Use a lightweight model (e.g., Haiku/Nova Lite) for query rewriting to minimize latency and cost. The rewrite call should add ~1-2 seconds. Configurable via `RAG_REWRITE_MODEL_ID` environment variable.

## 4. Answer Citation Module

### 4.1 Approach

Prompt-injection approach: instruct the LLM to use `[n]` markers referencing numbered source documents, then parse the response to extract which sources were actually cited.

### 4.2 Domain Layer

```java
// domain/rag/Citation.java
public record Citation(
    int index,
    String filename,
    Integer pageNumber,
    String sectionPath,
    String excerpt
) {}

// domain/rag/CitedAnswer.java
public record CitedAnswer(
    String answer,
    List<Citation> citations
) {}
```

### 4.3 Application Layer: CitationAssemblyService

```java
// application/rag/CitationAssemblyService.java
public class CitationAssemblyService {

    /** Assemble numbered document context for LLM prompt */
    public PromptWithCitations assemble(List<RetrievedDocument> documents) {
        // 1. Assign [1], [2], ... to each document
        // 2. Extract filename, page_number, section_path from metadata
        // 3. Build formatted context string with numbering
        // 4. Return { formattedContext, citationMap }
    }

    /** Parse LLM response to extract used citations */
    public CitedAnswer parseResponse(String llmAnswer, Map<Integer, Citation> citationMap) {
        // 1. Regex extract [1], [2] etc. from answer
        // 2. Keep only actually-cited citations
        // 3. Return CitedAnswer { answer, usedCitations }
    }
}
```

### 4.4 Prompt Template (Revised)

```
相关文档如下，每段文档前标注了编号、来源文件名和页码：

[1] (AML手册.pdf, 第12页)
KYC审查流程要求对所有新客户进行World-Check筛查...

[2] (开户操作指引.pdf, 第5页)
PI分类标准根据客户资产规模和交易频率确定...

请根据以上文档回答用户问题。回答时必须使用[编号]标注信息来源，例如"根据[1]，..."。
如果文档中没有相关信息，请说明无法找到答案，不要编造内容。

用户问题：
{query}
```

### 4.5 API Response Change (Backward Compatible)

```json
{
    "answer": "根据[1]，KYC审查流程要求...",
    "citations": [
        {
            "index": 1,
            "filename": "AML手册.pdf",
            "page_number": 12,
            "section_path": "第三章/KYC审查",
            "excerpt": "KYC审查流程要求对所有新客户..."
        }
    ],
    "source_documents": [...],
    "recall_documents": [...],
    "rerank_documents": [...]
}
```

- `citations` is a new field; `source_documents` preserved for backward compatibility
- Frontend renders citation cards below the answer; `[1]` markers can anchor-link to corresponding cards

### 4.6 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Citation generation | LLM prompt instruction | More accurate than post-hoc matching; LLM understands context relationships |
| Citation granularity | Document-level (per reranked doc) | Paragraph-level too fragmented; document-level most useful for users |
| Uncited documents | Excluded from citations | Only show sources the LLM actually used |
| Frontend compatibility | `source_documents` preserved | Old frontend unaffected; new frontend uses `citations` |

## 5. Offline Evaluation System

### 5.1 Architecture: RAGAS + Bedrock Evaluations

Java side collects pipeline trace data; Python RAGAS sidecar computes standardized metrics.

```
backend-java                              ragas-evaluator (Python sidecar)
─────────────                             ─────────────────────────────────
TestDataset (JSON)
  → EvaluationRunner
    │ Run full pipeline per case
    │ Collect TraceRecord[]
    ↓
  trace_output.json ───HTTP POST──→       FastAPI service
                                            │ ragas.evaluate()
                                            │ 4 RAGAS metrics
  evaluation_report.json ←─Response───      │
    │
    ↓
  + Custom metrics (Keyword Recall,
    Structured Parse Rate, Citation Accuracy)
    ↓
  Final Report
```

### 5.2 Test Dataset Format

```json
{
    "version": "1.0",
    "dataset": "cob_compliance",
    "cases": [
        {
            "id": "COB-001",
            "module": "cob",
            "query": "World-Check has returned a potential match. How should this be resolved?",
            "expected_keywords": ["AML", "World-Check", "name screening"],
            "expected_sources": [
                { "filename": "AML手册.pdf", "page_numbers": [12, 13] }
            ],
            "reference_answer": "根据AML手册第12页，World-Check匹配结果应...",
            "difficulty": "medium"
        },
        {
            "id": "COL-001",
            "module": "collateral",
            "query": "华泰和HSBC交易的ISDA&CSA下的minimum transfer amount是多少？",
            "expected_structured": {
                "counterparty": "HSBC",
                "agreement_type": "ISDA_CSA",
                "business_field": "minimum transfer amount"
            },
            "expected_sources": [
                { "filename": "HSBC_ISDA_CSA.pdf", "page_numbers": [8] }
            ],
            "reference_answer": "华泰与HSBC的ISDA/CSA协议中，MTA为...",
            "difficulty": "hard"
        }
    ]
}
```

### 5.3 Test Dataset Generation Strategy (Plan D)

1. Business team provides core cases (~20-30) → manual JSON entry
2. RAGAS `TestsetGenerator` auto-generates from indexed documents (~100-200 cases)
3. Human review of synthetic cases, discard low-quality items
4. Merge into final dataset under version control

### 5.4 Java Side: Trace Collector

```java
// evaluation/model/TraceRecord.java
public record TraceRecord(
    String caseId,
    String module,
    String originalQuery,
    String rewrittenQuery,
    List<String> extractedKeywords,
    StructuredQuery structuredQuery,
    List<String> retrievedContexts,
    String generatedAnswer,
    String referenceAnswer,
    List<ExpectedSource> expectedSources,
    long rewriteLatencyMs,
    long retrievalLatencyMs,
    long generationLatencyMs
) {}
```

### 5.5 Python Side: RAGAS Service

```python
# ragas-evaluator/app.py
from ragas import evaluate
from ragas.metrics import (
    faithfulness, answer_relevancy,
    context_precision, context_recall
)
from ragas.llms import BedrockLLM

@app.post("/evaluate")
async def run_evaluation(payload: EvalRequest):
    dataset = Dataset.from_dict(payload.traces)
    result = evaluate(
        dataset,
        metrics=[faithfulness, answer_relevancy,
                 context_precision, context_recall],
        llm=BedrockLLM(model_id="..."),
    )
    return result.to_pandas().to_dict()

@app.post("/generate_testset")
async def generate_testset(payload: DocRequest):
    generator = TestsetGenerator(llm=BedrockLLM(...))
    testset = generator.generate_with_langchain_docs(
        payload.documents, test_size=payload.size
    )
    return testset.to_dict()
```

### 5.6 Metrics

**RAGAS metrics (core):**

| Metric | Measures |
|--------|----------|
| Context Precision | Are relevant documents ranked high in retrieval results? |
| Context Recall | Are all claims in the reference answer covered by retrieved contexts? |
| Faithfulness | Is the answer grounded in source documents? (anti-hallucination) |
| Answer Relevancy | Does the answer address the user's question? |

**Custom metrics:**

| Metric | Measures |
|--------|----------|
| Keyword Recall | COB keyword extraction accuracy vs expected_keywords |
| Structured Parse Rate | Collateral triple parsing success rate per field |
| Citation Accuracy | Do `[n]` markers in the answer point to correct sources? |

**Bedrock Evaluations (supplementary):**

| Metric | Measures |
|--------|----------|
| Correctness | Factual correctness of answer (managed AWS service, zero deployment) |

### 5.7 Execution

```bash
# Start RAGAS service
cd ragas-evaluator && docker-compose up -d

# Run evaluation
mvn test -Ptag=evaluation -Dtest=RagEvaluationTest

# View report
cat evaluation-results/report-2026-03-26.json
```

### 5.8 Trace Fields for Future Full-Chain Observability

`TraceRecord` includes all fields needed for Batch B's Langfuse integration. When Langfuse is introduced, the same data structure feeds into OpenTelemetry spans without schema changes.

## 6. File Inventory

### New Files

| Layer | File | Purpose |
|-------|------|---------|
| domain | `QueryRewriteStrategy.java` | Strategy interface |
| domain | `RewriteResult.java` | Rewrite result value object |
| domain | `StructuredQuery.java` | Collateral triple value object |
| domain | `Citation.java` | Citation value object |
| domain | `CitedAnswer.java` | Answer with citations value object |
| application | `QueryRewriteRouter.java` | Strategy registry + routing |
| application | `CitationAssemblyService.java` | Citation assembly + response parsing |
| infrastructure | `CobKeywordRewriteStrategy.java` | COB keyword extraction via Bedrock |
| infrastructure | `CollateralStructuredRewriteStrategy.java` | Collateral triple parsing via Bedrock |
| evaluation | `TraceRecord.java` | Pipeline trace data |
| evaluation | `TestCase.java` `TestDataset.java` | Test dataset model |
| evaluation | `EvaluationReport.java` | Report model |
| evaluation | `EvaluationRunner.java` | Batch runner + trace collector |
| evaluation | `RagasClient.java` | HTTP client for RAGAS service |
| evaluation | `ReportGenerator.java` | Report generation |
| python | `ragas-evaluator/` | RAGAS FastAPI service + Dockerfile |
| test resources | `evaluation/*.json` | Test datasets |

### Modified Files

| File | Change |
|------|--------|
| `RagQueryApplicationService.java` | Insert query rewrite + citation steps |
| `RagResponse.java` | Add `citations` field (backward compatible) |
| `RetrievalPort.java` | Add metadata filter parameter |
| `OpenSearchRetrievalAdapter.java` | Implement metadata filter retrieval |
| `ApplicationWiringConfig.java` | Wire new beans |
| Prompt template (in `BedrockAnswerGenerationAdapter`) | Add citation numbering instructions |

### Unchanged

- Frontend (Batch B consumes `citations`)
- BDA / Docling parsing pipeline
- Rerank logic
- PostgreSQL schema
- Existing API endpoint signatures

## 7. Key Decisions Summary

| # | Decision | Conclusion |
|---|----------|------------|
| 1 | Batch scope | A = Query Rewrite + Citation + Evaluation |
| 2 | Evaluation depth | Offline first; design reserves full-chain trace fields |
| 3 | Query rewriting | Strategy pattern, dual-path: COB keywords + Collateral structured |
| 4 | Collateral metadata | Directory structure extraction + LLM extraction at parse time |
| 5 | Answer citation | Prompt-injection `[n]` markers + response parsing; doc name + page + excerpt |
| 6 | Evaluation framework | RAGAS (Python sidecar) + Bedrock Evaluations + custom metrics |
| 7 | Test dataset | Business core cases + RAGAS auto-generation + human review |
| 8 | Evaluation metrics | RAGAS 4 metrics (retrieval-focused) + 3 custom + Bedrock Correctness |
| 9 | AI framework | Batch A hand-written; Spring AI deferred to Batch B |
| 10 | Design pattern | Strategy + Registry for query rewrite extensibility |

## 8. Future: Batch B Scope (Not This Design)

- Multi-turn conversation (Session Memory)
- Streaming output (SSE)
- User feedback mechanism (thumbs up/down)
- Langfuse integration (full-chain tracing + dashboard)
- Spring AI framework migration
