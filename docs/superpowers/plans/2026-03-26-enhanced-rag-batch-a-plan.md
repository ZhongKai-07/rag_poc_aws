# Enhanced RAG Batch A Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add query rewriting (dual-path COB/Collateral), answer citation with source traceability, and RAGAS-based offline evaluation to the existing RAG pipeline.

**Architecture:** Pipeline-inline enhancement using Strategy pattern for query rewriting, prompt-injection for citations, and a Python RAGAS sidecar for evaluation metrics. All changes follow the existing hexagonal architecture — new domain ports, application services, and infrastructure adapters.

**Tech Stack:** Java 17, Spring Boot 3.4, AWS Bedrock Converse API, OpenSearch, PostgreSQL, Python 3.11 + RAGAS + FastAPI

**Spec:** `docs/superpowers/specs/2026-03-26-enhanced-rag-batch-a-design.md`

**User review notes (implementation attention items):**
1. OpenSearch metadata fields (`counterparty`, `agreement_type`) must be `keyword` type — define explicitly in `ensureIndex()` mapping
2. `CitationAssemblyService` must handle null `filename` defensively — fall back to indexName
3. Citation prompt should include 1-2 few-shot examples; regex must handle `[1,2]` / `[1][2]` variants
4. Use `CompletableFuture.orTimeout()` for rewrite 3-second hard timeout
5. `RagasClient` base URL configurable via `RAGAS_EVALUATOR_URL` env var
6. Add `rag.citation.enabled` feature flag alongside `rag.query-rewrite.enabled`

---

## Chunk 1: Foundation — Domain Value Objects + Configuration

### Task 1: Domain Value Objects for Query Rewriting

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/RewriteResult.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/StructuredQuery.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/QueryRewriteStrategy.java`
- Test: `backend-java/src/test/java/com/huatai/rag/domain/QueryRewriteValueObjectsTest.java`

- [ ] **Step 1: Write test for domain value objects**

```java
package com.huatai.rag.domain;

import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.domain.rag.StructuredQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteValueObjectsTest {

    @Test
    void rewriteResult_holds_all_fields() {
        var result = new RewriteResult("rewritten", List.of("AML", "KYC"), null, "original");
        assertThat(result.rewrittenQuery()).isEqualTo("rewritten");
        assertThat(result.keywords()).containsExactly("AML", "KYC");
        assertThat(result.structured()).isNull();
        assertThat(result.originalQuery()).isEqualTo("original");
    }

    @Test
    void structuredQuery_holds_all_fields() {
        var sq = new StructuredQuery("HSBC", "ISDA_CSA", "minimum transfer amount", "fallback");
        assertThat(sq.counterparty()).isEqualTo("HSBC");
        assertThat(sq.agreementType()).isEqualTo("ISDA_CSA");
        assertThat(sq.businessField()).isEqualTo("minimum transfer amount");
        assertThat(sq.fallbackQuery()).isEqualTo("fallback");
    }

    @Test
    void rewriteResult_passthrough_factory() {
        var result = RewriteResult.passthrough("raw query");
        assertThat(result.rewrittenQuery()).isEqualTo("raw query");
        assertThat(result.keywords()).isEmpty();
        assertThat(result.structured()).isNull();
        assertThat(result.originalQuery()).isEqualTo("raw query");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test -Dtest=QueryRewriteValueObjectsTest -pl .`
Expected: FAIL — classes do not exist yet

- [ ] **Step 3: Create domain value objects**

`RewriteResult.java`:
```java
package com.huatai.rag.domain.rag;

import java.util.List;

public record RewriteResult(
        String rewrittenQuery,
        List<String> keywords,
        StructuredQuery structured,
        String originalQuery
) {
    public static RewriteResult passthrough(String originalQuery) {
        return new RewriteResult(originalQuery, List.of(), null, originalQuery);
    }
}
```

`StructuredQuery.java`:
```java
package com.huatai.rag.domain.rag;

public record StructuredQuery(
        String counterparty,
        String agreementType,
        String businessField,
        String fallbackQuery
) {}
```

`QueryRewriteStrategy.java`:
```java
package com.huatai.rag.domain.rag;

public interface QueryRewriteStrategy {
    boolean supports(String module);
    RewriteResult rewrite(String query);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test -Dtest=QueryRewriteValueObjectsTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/rag/RewriteResult.java \
       backend-java/src/main/java/com/huatai/rag/domain/rag/StructuredQuery.java \
       backend-java/src/main/java/com/huatai/rag/domain/rag/QueryRewriteStrategy.java \
       backend-java/src/test/java/com/huatai/rag/domain/QueryRewriteValueObjectsTest.java
git commit -m "feat: add query rewrite domain value objects and strategy interface"
```

---

### Task 2: Domain Value Objects for Citation

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/Citation.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/CitedAnswer.java`
- Test: `backend-java/src/test/java/com/huatai/rag/domain/CitationValueObjectsTest.java`

- [ ] **Step 1: Write test**

```java
package com.huatai.rag.domain;

import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.rag.CitedAnswer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CitationValueObjectsTest {

    @Test
    void citation_holds_all_fields() {
        var c = new Citation(1, "AML手册.pdf", 12, "第三章/KYC审查", "KYC审查流程...");
        assertThat(c.index()).isEqualTo(1);
        assertThat(c.filename()).isEqualTo("AML手册.pdf");
        assertThat(c.pageNumber()).isEqualTo(12);
        assertThat(c.sectionPath()).isEqualTo("第三章/KYC审查");
        assertThat(c.excerpt()).isEqualTo("KYC审查流程...");
    }

    @Test
    void citation_handles_null_optional_fields() {
        var c = new Citation(1, "未知源文档", null, null, "some text");
        assertThat(c.pageNumber()).isNull();
        assertThat(c.sectionPath()).isNull();
    }

    @Test
    void citedAnswer_holds_answer_and_citations() {
        var citations = List.of(new Citation(1, "f.pdf", 1, null, "text"));
        var ca = new CitedAnswer("根据[1]...", citations);
        assertThat(ca.answer()).isEqualTo("根据[1]...");
        assertThat(ca.citations()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**
- [ ] **Step 3: Create Citation.java and CitedAnswer.java**

```java
// Citation.java
package com.huatai.rag.domain.rag;

public record Citation(
        int index,
        String filename,
        Integer pageNumber,
        String sectionPath,
        String excerpt
) {}

// CitedAnswer.java
package com.huatai.rag.domain.rag;

import java.util.List;

public record CitedAnswer(
        String answer,
        List<Citation> citations
) {}
```

- [ ] **Step 4: Run test — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/rag/Citation.java \
       backend-java/src/main/java/com/huatai/rag/domain/rag/CitedAnswer.java \
       backend-java/src/test/java/com/huatai/rag/domain/CitationValueObjectsTest.java
git commit -m "feat: add citation domain value objects"
```

---

### Task 3: RetrievalRequest Value Object + RetrievalPort Signature Change

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalRequest.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalPort.java` (line 7-14)
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java` (line 40-58)
- Modify: `backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java` (line 72-82)
- Modify: `backend-java/src/test/java/com/huatai/rag/application/RagQueryApplicationServiceTest.java`

- [ ] **Step 1: Create RetrievalRequest value object**

```java
package com.huatai.rag.domain.retrieval;

import java.util.List;
import java.util.Map;

public record RetrievalRequest(
        List<String> indexNames,
        String query,
        SearchMethod searchMethod,
        int vectorLimit,
        int textLimit,
        double vectorScoreThreshold,
        double textScoreThreshold,
        Map<String, String> metadataFilters
) {
    public RetrievalRequest(List<String> indexNames, String query, SearchMethod searchMethod,
                            int vectorLimit, int textLimit,
                            double vectorScoreThreshold, double textScoreThreshold) {
        this(indexNames, query, searchMethod, vectorLimit, textLimit,
                vectorScoreThreshold, textScoreThreshold, null);
    }
}
```

- [ ] **Step 2: Update RetrievalPort interface**

Change `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalPort.java`:

```java
// Before (7 params):
RetrievalResult retrieve(List<String> indexNames, String query, SearchMethod searchMethod,
                         int vectorLimit, int textLimit,
                         double vectorScoreThreshold, double textScoreThreshold);

// After (1 param):
RetrievalResult retrieve(RetrievalRequest request);
```

- [ ] **Step 3: Update OpenSearchRetrievalAdapter.retrieve()**

Change `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java` line 40. **Important:** Keep the existing `searchGateway.vectorSearch()` 4-param call for now — metadata filter support is added in Task 8. This task only unwraps `RetrievalRequest` fields:

```java
@Override
public RetrievalResult retrieve(RetrievalRequest request) {
    List<RetrievedDocument> results = switch (request.searchMethod()) {
        case VECTOR -> searchGateway.vectorSearch(
                request.indexNames(), request.query(),
                request.vectorLimit(), request.vectorScoreThreshold());
        case TEXT -> searchGateway.textSearch(
                request.indexNames(), request.query(),
                request.textLimit(), request.textScoreThreshold());
        case MIX -> mergeUnique(
                searchGateway.vectorSearch(
                        request.indexNames(), request.query(),
                        request.vectorLimit(), request.vectorScoreThreshold()),
                searchGateway.textSearch(
                        request.indexNames(), request.query(),
                        request.textLimit(), request.textScoreThreshold()),
                request.vectorLimit() + request.textLimit());
    };
    return new RetrievalResult(results, results);
}
```

`metadataFilters` is ignored here; Task 8 will add the filtered query path.

- [ ] **Step 4: Update RagQueryApplicationService call site**

Change `backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java` line 74:

```java
// Before:
RetrievalResult retrievalResult = retrievalPort.retrieve(
        command.indexNames(), command.query(), SearchMethod.fromValue(command.searchMethod()),
        command.vecDocsNum(), command.txtDocsNum(),
        command.vecScoreThreshold(), command.textScoreThreshold());

// After:
var retrievalRequest = new RetrievalRequest(
        command.indexNames(), command.query(),
        SearchMethod.fromValue(command.searchMethod()),
        command.vecDocsNum(), command.txtDocsNum(),
        command.vecScoreThreshold(), command.textScoreThreshold());
RetrievalResult retrievalResult = retrievalPort.retrieve(retrievalRequest);
```

- [ ] **Step 5: Fix all compilation errors in tests**

Update `RagQueryApplicationServiceTest.java` — the `FakeRagQueryDependencies` constructor has a lambda for `RetrievalPort` matching the old 7-param signature. Replace with:

```java
// Before (7-param lambda):
(indexNames, query, searchMethod, vectorLimit, textLimit, vectorScoreThreshold, textScoreThreshold) -> { ... }

// After (single-param RetrievalRequest):
(request) -> { /* same body, use request.indexNames(), request.query(), etc. */ }
```

Also update any other tests that mock or implement `RetrievalPort`.

- [ ] **Step 6: Run full test suite**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test`
Expected: All existing tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalRequest.java \
       backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalPort.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java \
       backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java \
       backend-java/src/test/
git commit -m "refactor: replace RetrievalPort 7-param signature with RetrievalRequest value object"
```

---

### Task 4: Configuration — Feature Flags + Rewrite Model ID

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/RagProperties.java`
- Modify: `backend-java/src/main/resources/application.yml`
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/config/RagPropertiesFeatureFlagTest.java`

- [ ] **Step 1: Write test for new properties**

```java
package com.huatai.rag.infrastructure.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesFeatureFlagTest {

    @Test
    void queryRewrite_enabled_by_default() {
        var props = new RagProperties();
        assertThat(props.isQueryRewriteEnabled()).isTrue();
    }

    @Test
    void citation_enabled_by_default() {
        var props = new RagProperties();
        assertThat(props.isCitationEnabled()).isTrue();
    }

    @Test
    void rewriteModelId_has_default() {
        var props = new RagProperties();
        assertThat(props.getRewriteModelId()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Add properties to RagProperties.java**

Add to `backend-java/src/main/java/com/huatai/rag/infrastructure/config/RagProperties.java`:

```java
private boolean queryRewriteEnabled = true;
private boolean citationEnabled = true;
private String rewriteModelId = "amazon.nova-lite-v1:0";

// getters and setters
public boolean isQueryRewriteEnabled() { return queryRewriteEnabled; }
public void setQueryRewriteEnabled(boolean queryRewriteEnabled) { this.queryRewriteEnabled = queryRewriteEnabled; }

public boolean isCitationEnabled() { return citationEnabled; }
public void setCitationEnabled(boolean citationEnabled) { this.citationEnabled = citationEnabled; }

public String getRewriteModelId() { return rewriteModelId; }
public void setRewriteModelId(String rewriteModelId) { this.rewriteModelId = rewriteModelId; }
```

- [ ] **Step 4: Add to application.yml**

Append to `huatai.rag` section:
```yaml
    query-rewrite-enabled: ${RAG_QUERY_REWRITE_ENABLED:true}
    citation-enabled: ${RAG_CITATION_ENABLED:true}
    rewrite-model-id: ${RAG_REWRITE_MODEL_ID:amazon.nova-lite-v1:0}
```

- [ ] **Step 5: Run test — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/config/RagProperties.java \
       backend-java/src/main/resources/application.yml \
       backend-java/src/test/java/com/huatai/rag/infrastructure/config/RagPropertiesFeatureFlagTest.java
git commit -m "feat: add feature flags and rewrite model config"
```

---

## Chunk 2: Query Rewriting Module

### Task 5: QueryRewriteRouter (Application Layer)

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/application/rag/QueryRewriteRouter.java`
- Test: `backend-java/src/test/java/com/huatai/rag/application/QueryRewriteRouterTest.java`

- [ ] **Step 1: Write test**

```java
package com.huatai.rag.application;

import com.huatai.rag.application.rag.QueryRewriteRouter;
import com.huatai.rag.domain.rag.QueryRewriteStrategy;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryRewriteRouterTest {

    @Test
    void routes_to_matching_strategy() {
        var cobStrategy = mock(QueryRewriteStrategy.class);
        when(cobStrategy.supports("cob")).thenReturn(true);
        when(cobStrategy.rewrite("test")).thenReturn(
                new RewriteResult("rewritten", List.of("KYC"), null, "test"));

        var props = new RagProperties();
        var router = new QueryRewriteRouter(List.of(cobStrategy), cobStrategy, props);

        var result = router.rewrite("test", "cob");
        assertThat(result.rewrittenQuery()).isEqualTo("rewritten");
    }

    @Test
    void falls_back_to_default_for_unknown_module() {
        var defaultStrategy = mock(QueryRewriteStrategy.class);
        when(defaultStrategy.supports(anyString())).thenReturn(false);
        when(defaultStrategy.rewrite("test")).thenReturn(RewriteResult.passthrough("test"));

        var props = new RagProperties();
        var router = new QueryRewriteRouter(List.of(), defaultStrategy, props);

        var result = router.rewrite("test", "RAG");
        assertThat(result.rewrittenQuery()).isEqualTo("test");
    }

    @Test
    void returns_passthrough_when_disabled() {
        var props = new RagProperties();
        props.setQueryRewriteEnabled(false);

        var strategy = mock(QueryRewriteStrategy.class);
        var router = new QueryRewriteRouter(List.of(strategy), strategy, props);

        var result = router.rewrite("test", "cob");
        assertThat(result.rewrittenQuery()).isEqualTo("test");
        assertThat(result.originalQuery()).isEqualTo("test");
        verify(strategy, never()).rewrite(anyString());
    }

    @Test
    void catches_strategy_exception_and_returns_passthrough() {
        var strategy = mock(QueryRewriteStrategy.class);
        when(strategy.supports("cob")).thenReturn(true);
        when(strategy.rewrite(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        var props = new RagProperties();
        var router = new QueryRewriteRouter(List.of(strategy), strategy, props);

        var result = router.rewrite("test", "cob");
        assertThat(result.rewrittenQuery()).isEqualTo("test");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement QueryRewriteRouter**

```java
package com.huatai.rag.application.rag;

import com.huatai.rag.domain.rag.QueryRewriteStrategy;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class QueryRewriteRouter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteRouter.class);

    private final List<QueryRewriteStrategy> strategies;
    private final QueryRewriteStrategy defaultStrategy;
    private final RagProperties ragProperties;

    public QueryRewriteRouter(List<QueryRewriteStrategy> strategies,
                              QueryRewriteStrategy defaultStrategy,
                              RagProperties ragProperties) {
        this.strategies = strategies;
        this.defaultStrategy = defaultStrategy;
        this.ragProperties = ragProperties;
    }

    public RewriteResult rewrite(String query, String module) {
        if (!ragProperties.isQueryRewriteEnabled()) {
            return RewriteResult.passthrough(query);
        }

        try {
            QueryRewriteStrategy selected = strategies.stream()
                    .filter(s -> s.supports(module))
                    .findFirst()
                    .orElse(defaultStrategy);
            return selected.rewrite(query);
        } catch (Exception e) {
            log.warn("Query rewrite failed, falling back to original query: {}", e.getMessage());
            return RewriteResult.passthrough(query);
        }
    }
}
```

- [ ] **Step 4: Run test — expect PASS (4 tests)**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/rag/QueryRewriteRouter.java \
       backend-java/src/test/java/com/huatai/rag/application/QueryRewriteRouterTest.java
git commit -m "feat: add QueryRewriteRouter with strategy routing and graceful degradation"
```

---

### Task 6: CobKeywordRewriteStrategy (Infrastructure Layer)

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/CobKeywordRewriteStrategy.java`
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/CobKeywordRewriteStrategyTest.java`

- [ ] **Step 1: Write test (mocked Bedrock)**

```java
package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CobKeywordRewriteStrategyTest {

    @Test
    void supports_cob_module() {
        var strategy = new CobKeywordRewriteStrategy(mock(BedrockRuntimeClient.class), new RagProperties());
        assertThat(strategy.supports("cob")).isTrue();
        assertThat(strategy.supports("COB")).isTrue();
        assertThat(strategy.supports("collateral")).isFalse();
    }

    @Test
    void parses_valid_json_response() {
        var client = mock(BedrockRuntimeClient.class);
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText(
                                        "{\"rewritten_query\": \"AML KYC审查流程\", \"keywords\": [\"AML\", \"KYC\"]}"))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CobKeywordRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("AML审查怎么做");

        assertThat(result.rewrittenQuery()).isEqualTo("AML KYC审查流程");
        assertThat(result.keywords()).containsExactly("AML", "KYC");
        assertThat(result.originalQuery()).isEqualTo("AML审查怎么做");
    }

    @Test
    void falls_back_on_malformed_json() {
        var client = mock(BedrockRuntimeClient.class);
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText("not valid json"))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CobKeywordRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("test query");

        assertThat(result.rewrittenQuery()).isEqualTo("test query");
        assertThat(result.keywords()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement CobKeywordRewriteStrategy**

Key implementation details:
- Uses `BedrockRuntimeClient.converse()` with rewrite model ID from `RagProperties`
- Prompt template from spec Section 3.4
- Parses JSON response with Jackson `ObjectMapper`
- On any parse failure, returns `RewriteResult.passthrough(originalQuery)`
- Uses `CompletableFuture.orTimeout(3, TimeUnit.SECONDS)` for hard timeout

- [ ] **Step 4: Run test — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/CobKeywordRewriteStrategy.java \
       backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/CobKeywordRewriteStrategyTest.java
git commit -m "feat: add COB keyword extraction rewrite strategy"
```

---

### Task 7: CollateralStructuredRewriteStrategy (Infrastructure Layer)

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/CollateralStructuredRewriteStrategy.java`
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/CollateralStructuredRewriteStrategyTest.java`

- [ ] **Step 1: Write test (mocked Bedrock)**

```java
package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollateralStructuredRewriteStrategyTest {

    @Test
    void supports_collateral_module() {
        var strategy = new CollateralStructuredRewriteStrategy(mock(BedrockRuntimeClient.class), new RagProperties());
        assertThat(strategy.supports("collateral")).isTrue();
        assertThat(strategy.supports("COLLATERAL")).isTrue();
        assertThat(strategy.supports("cob")).isFalse();
    }

    @Test
    void parses_full_structured_response() {
        var client = mock(BedrockRuntimeClient.class);
        var json = """
                {"counterparty": "HSBC", "agreement_type": "ISDA_CSA",
                 "business_field": "minimum transfer amount",
                 "fallback_query": "HSBC ISDA MTA"}""";
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText(json))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CollateralStructuredRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("华泰和HSBC的ISDA下MTA是多少");

        assertThat(result.structured()).isNotNull();
        assertThat(result.structured().counterparty()).isEqualTo("HSBC");
        assertThat(result.structured().agreementType()).isEqualTo("ISDA_CSA");
        assertThat(result.structured().businessField()).isEqualTo("minimum transfer amount");
        assertThat(result.rewrittenQuery()).isEqualTo("minimum transfer amount");
    }

    @Test
    void uses_fallback_on_partial_parse() {
        var client = mock(BedrockRuntimeClient.class);
        var json = """
                {"counterparty": null, "agreement_type": null,
                 "business_field": null, "fallback_query": "MTA查询"}""";
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText(json))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CollateralStructuredRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("MTA是什么");

        assertThat(result.rewrittenQuery()).isEqualTo("MTA查询");
        assertThat(result.structured().counterparty()).isNull();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement CollateralStructuredRewriteStrategy**

Key implementation details:
- Same Bedrock Converse pattern as COB strategy
- Prompt template from spec Section 3.5
- `rewrittenQuery` is set to `businessField` when structured parse succeeds, `fallbackQuery` otherwise
- Metadata filters built from non-null `counterparty` + `agreementType` fields

- [ ] **Step 4: Run test — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/CollateralStructuredRewriteStrategy.java \
       backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/CollateralStructuredRewriteStrategyTest.java
git commit -m "feat: add Collateral structured query rewrite strategy"
```

---

### Task 8: OpenSearch Metadata Filter Support

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchQueryBuilder.java` — static helper for building KNN/filtered queries
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java` (vectorSearch method, lines 87-101) — use `OpenSearchQueryBuilder`, pass `metadataFilters` from `RetrievalRequest`
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchIndexManager.java` — add `metadata.counterparty` and `metadata.agreement_type` as `keyword` type in index mapping (user review note #1)
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/opensearch/MetadataFilterQueryTest.java`

- [ ] **Step 1: Write test for metadata filter query construction**

```java
package com.huatai.rag.infrastructure.opensearch;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MetadataFilterQueryTest {

    @Test
    void builds_knn_query_without_filter_when_null() {
        var body = OpenSearchQueryBuilder.buildVectorQuery(
                List.of(0.1f, 0.2f), 5, null);
        assertThat(body).containsKey("query");
        var query = (Map<String, Object>) body.get("query");
        assertThat(query).containsKey("knn");
        assertThat(query).doesNotContainKey("bool");
    }

    @Test
    void builds_bool_knn_filter_query_when_filters_present() {
        var filters = Map.of("counterparty", "HSBC", "agreement_type", "ISDA_CSA");
        var body = OpenSearchQueryBuilder.buildVectorQuery(
                List.of(0.1f, 0.2f), 5, filters);
        var query = (Map<String, Object>) body.get("query");
        assertThat(query).containsKey("bool");
        var bool = (Map<String, Object>) query.get("bool");
        assertThat(bool).containsKey("must");
        assertThat(bool).containsKey("filter");
        var filterList = (List<?>) bool.get("filter");
        assertThat(filterList).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Create OpenSearchQueryBuilder helper and update vectorSearch**

Extract query building into a static helper `OpenSearchQueryBuilder` for testability. Update `vectorSearch()` in `OpenSearchRetrievalAdapter` to pass `metadataFilters` through.

- [ ] **Step 4: Update `OpenSearchIndexManager.ensureIndex()` mapping**

Add explicit `keyword` type for Collateral metadata fields in the index mapping definition:

```json
"metadata": {
    "properties": {
        "counterparty": { "type": "keyword" },
        "agreement_type": { "type": "keyword" },
        "filename": { "type": "keyword" },
        "page_number": { "type": "integer" },
        "section_path": { "type": "keyword" }
    }
}
```

This ensures `term` queries work without needing `.keyword` sub-field (user review note #1).

- [ ] **Step 5: Run test — expect PASS**
- [ ] **Step 6: Run full test suite to verify no regressions**
- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/ \
       backend-java/src/test/java/com/huatai/rag/infrastructure/opensearch/MetadataFilterQueryTest.java
git commit -m "feat: add OpenSearch metadata filter support and keyword mappings for Collateral queries"
```

---

## Chunk 3: Answer Citation Module

### Task 9: CitationAssemblyService

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/application/rag/CitationAssemblyService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/application/rag/PromptWithCitations.java`
- Test: `backend-java/src/test/java/com/huatai/rag/application/CitationAssemblyServiceTest.java`

- [ ] **Step 1: Write test**

```java
package com.huatai.rag.application;

import com.huatai.rag.application.rag.CitationAssemblyService;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CitationAssemblyServiceTest {

    @Test
    void assemble_numbers_documents_and_extracts_metadata() {
        var docs = List.of(
                new RetrievedDocument("KYC审查流程...", 0.9, null,
                        Map.of("filename", "AML手册.pdf", "page_number", 12,
                                "section_path", List.of("第三章", "KYC审查"))),
                new RetrievedDocument("PI分类标准...", 0.8, null,
                        Map.of("filename", "开户指引.pdf", "page_number", 5,
                                "section_path", List.of("第二章"))));

        var service = new CitationAssemblyService();
        var result = service.assemble(docs);

        assertThat(result.formattedContext()).contains("[1] (AML手册.pdf, 第12页)");
        assertThat(result.formattedContext()).contains("[2] (开户指引.pdf, 第5页)");
        assertThat(result.citationMap()).hasSize(2);
        assertThat(result.citationMap().get(1).filename()).isEqualTo("AML手册.pdf");
        assertThat(result.citationMap().get(1).sectionPath()).isEqualTo("第三章/KYC审查");
    }

    @Test
    void assemble_falls_back_to_indexName_when_filename_missing() {
        var docs = List.of(
                new RetrievedDocument("text", 0.9, null, Map.of("page_number", 1)));
        var service = new CitationAssemblyService();
        var result = service.assemble(docs);
        assertThat(result.citationMap().get(1).filename()).isEqualTo("未知源文档");
    }

    @Test
    void parseResponse_extracts_cited_references() {
        var service = new CitationAssemblyService();
        var citations = Map.of(
                1, new com.huatai.rag.domain.rag.Citation(1, "a.pdf", 1, null, "text1"),
                2, new com.huatai.rag.domain.rag.Citation(2, "b.pdf", 2, null, "text2"),
                3, new com.huatai.rag.domain.rag.Citation(3, "c.pdf", 3, null, "text3"));

        var cited = service.parseResponse("根据[1]和[3]，答案是...", citations);
        assertThat(cited.citations()).hasSize(2);
        assertThat(cited.citations().get(0).index()).isEqualTo(1);
        assertThat(cited.citations().get(1).index()).isEqualTo(3);
    }

    @Test
    void parseResponse_handles_variant_formats() {
        var service = new CitationAssemblyService();
        var citations = Map.of(
                1, new com.huatai.rag.domain.rag.Citation(1, "a.pdf", 1, null, "t"),
                2, new com.huatai.rag.domain.rag.Citation(2, "b.pdf", 2, null, "t"));

        // [1][2] adjacent format
        var cited = service.parseResponse("答案[1][2]如下", citations);
        assertThat(cited.citations()).hasSize(2);
    }

    @Test
    void parseResponse_returns_empty_citations_when_none_found() {
        var service = new CitationAssemblyService();
        var cited = service.parseResponse("没有引用的答案", Map.of());
        assertThat(cited.citations()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement PromptWithCitations value object**

```java
package com.huatai.rag.application.rag;

import com.huatai.rag.domain.rag.Citation;
import java.util.Map;

public record PromptWithCitations(
        String formattedContext,
        Map<Integer, Citation> citationMap
) {}
```

- [ ] **Step 4: Implement CitationAssemblyService**

Key implementation details:
- `assemble()`: iterates docs, extracts `filename` (fallback `"未知源文档"`), `page_number`, `section_path` (join `List<String>` with `/`), builds numbered prompt context with citation instructions and few-shot example
- `parseResponse()`: regex `\[(\d+)\]` extracts all citation indices, filters `citationMap` to only used ones, returns `CitedAnswer`
- Prompt includes few-shot example per user review note #3

- [ ] **Step 5: Run test — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/rag/CitationAssemblyService.java \
       backend-java/src/main/java/com/huatai/rag/application/rag/PromptWithCitations.java \
       backend-java/src/test/java/com/huatai/rag/application/CitationAssemblyServiceTest.java
git commit -m "feat: add CitationAssemblyService with prompt assembly and response parsing"
```

---

### Task 10: AnswerGenerationPort Signature Change

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/domain/rag/AnswerGenerationPort.java` (line 8)
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java` (lines 35-59)
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/PromptTemplateFactory.java` (lines 17-27)
- Modify: All tests referencing the old signature

- [ ] **Step 1: Update AnswerGenerationPort interface**

```java
// Before:
String generateAnswer(String query, List<RetrievedDocument> sourceDocuments);

// After:
String generateAnswer(String query, String formattedContext);
```

- [ ] **Step 2: Update BedrockAnswerGenerationAdapter**

```java
@Override
public String generateAnswer(String query, String formattedContext) {
    return retryUtils.executeWithRetry(() -> invoke(query, formattedContext),
            ragProperties.getRetryMaxAttempts(),
            ragProperties.getRetryBackoff());
}

private String invoke(String query, String formattedContext) {
    String userContent = formattedContext + "\n\n用户问题:\n" + query;
    String systemPrompt = promptTemplateFactory.buildSystemPrompt();
    // ... rest of Bedrock converse call unchanged
}
```

- [ ] **Step 3: Simplify PromptTemplateFactory**

Keep `buildContextFirstPrompt()` for the citation-disabled fallback path (when `rag.citation.enabled=false`, the pipeline still needs to format context for the LLM). Change its signature from `(String, List<RetrievedDocument>)` to `(String, String)` to accept pre-concatenated context text. Keep `buildSystemPrompt()` and `buildUserPrompt()`.

**Note:** Update `PromptTemplateFactoryTest.userPromptPlacesContextBeforeQuestion()` test to match the new signature.

- [ ] **Step 4: Fix all compilation errors in tests**

Key test updates needed:

1. `RagQueryApplicationServiceTest.java` — the `FakeRagQueryDependencies` has an `AnswerGenerationPort` lambda `(query, sourceDocuments) -> answer`. Update to `(query, formattedContext) -> answer` (same lambda shape, just different param semantics).
2. `PromptTemplateFactoryTest.java` — update `userPromptPlacesContextBeforeQuestion()` test for new `buildContextFirstPrompt(String, String)` signature.
3. `BedrockAdapterWiringTest.java` — verify the wiring test still compiles with the new adapter signature.

- [ ] **Step 5: Run full test suite**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/rag/AnswerGenerationPort.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/PromptTemplateFactory.java \
       backend-java/src/test/
git commit -m "refactor: simplify AnswerGenerationPort to accept pre-formatted context string"
```

---

### Task 11: RagResponse Citations DTO

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/CitationDto.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagResponse.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/api/RagControllerContractTest.java`

- [ ] **Step 1: Create CitationDto**

```java
package com.huatai.rag.api.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CitationDto {
    private int index;
    private String filename;
    @JsonProperty("page_number")
    private Integer pageNumber;
    @JsonProperty("section_path")
    private String sectionPath;
    private String excerpt;

    // Constructor, getters, setters
}
```

- [ ] **Step 2: Add citations field to RagResponse**

```java
// Add to RagResponse.java:
@JsonProperty("citations")
private List<CitationDto> citations = new ArrayList<>();

// getter + setter
```

- [ ] **Step 3: Update RagControllerContractTest to verify citations field present in response**
- [ ] **Step 4: Run tests — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/api/rag/dto/CitationDto.java \
       backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagResponse.java \
       backend-java/src/test/
git commit -m "feat: add citations field to RagResponse (backward compatible)"
```

---

### Task 12: Add Filename to OpenSearch Document Metadata

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java` — inject filename into chunk metadata before indexing
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchChunkMapper.java` (line 11-23) — read filename from chunk metadata
- Test: Update `DocumentIngestionApplicationServiceTest` to verify filename propagation

**Full injection chain:** `DocumentIngestionApplicationService` has access to `DocumentFileRecord.filename()`. Before calling `documentChunkWriter.writeChunks()`, inject filename into each `ParsedChunk`'s metadata map. `OpenSearchChunkMapper.toDocument()` will then naturally include it since it starts with `chunk.metadata()`.

- [ ] **Step 1: In `DocumentIngestionApplicationService`, inject filename into chunk metadata**

After BDA parsing produces `ParsedDocument` with chunks, iterate and add filename to each chunk's metadata:

```java
// Before writing chunks:
List<ParsedChunk> enrichedChunks = parsedDocument.chunks().stream()
        .map(chunk -> {
            var metadata = new LinkedHashMap<>(chunk.metadata());
            metadata.put("filename", documentFile.filename());
            return new ParsedChunk(chunk.chunkId(), chunk.pageNumber(),
                    chunk.paragraphText(), chunk.sentenceText(),
                    chunk.sectionPath(), chunk.assets(), metadata);
        })
        .toList();
```

- [ ] **Step 2: Verify `OpenSearchChunkMapper.toDocument()` already reads from `chunk.metadata()`**

Existing code: `Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());` — this already copies all metadata including the newly injected `filename`. No change needed in the mapper.

- [ ] **Step 3: Update `DocumentIngestionApplicationServiceTest` to verify filename is in chunk metadata**
- [ ] **Step 4: Run tests**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java \
       backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java
git commit -m "feat: inject filename into chunk metadata for citation support"
```

---

## Chunk 4: Pipeline Integration

### Task 13: Wire Everything Together in RagQueryApplicationService

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java` (handle method, lines 72-112)
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/application/RagQueryApplicationServiceTest.java`

- [ ] **Step 1: Update ApplicationWiringConfig to wire new beans**

Add to `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java`:

```java
@Bean
public CobKeywordRewriteStrategy cobKeywordRewriteStrategy(
        BedrockRuntimeClient bedrockRuntimeClient, RagProperties ragProperties) {
    return new CobKeywordRewriteStrategy(bedrockRuntimeClient, ragProperties);
}

@Bean
public CollateralStructuredRewriteStrategy collateralStructuredRewriteStrategy(
        BedrockRuntimeClient bedrockRuntimeClient, RagProperties ragProperties) {
    return new CollateralStructuredRewriteStrategy(bedrockRuntimeClient, ragProperties);
}

@Bean
public QueryRewriteRouter queryRewriteRouter(
        List<QueryRewriteStrategy> strategies,
        CobKeywordRewriteStrategy defaultStrategy,
        RagProperties ragProperties) {
    return new QueryRewriteRouter(strategies, defaultStrategy, ragProperties);
}

@Bean
public CitationAssemblyService citationAssemblyService() {
    return new CitationAssemblyService();
}
```

- [ ] **Step 2: Update `RagQueryApplicationService.Default` inner class constructor and `handle()`**

**Important:** `RagQueryApplicationService` is an **interface** with a `final class Default` nested inside (line 51). Add `QueryRewriteRouter`, `CitationAssemblyService`, and `RagProperties` as constructor dependencies to `Default`. Update `handle()`:

```java
@Override
public QueryResult handle(QueryCommand command) {
    // 1. Query rewriting
    RewriteResult rewriteResult = queryRewriteRouter.rewrite(command.query(), command.module());

    // 2. Build retrieval request (with metadata filters for Collateral)
    Map<String, String> metadataFilters = null;
    if (rewriteResult.structured() != null) {
        metadataFilters = buildMetadataFilters(rewriteResult.structured());
    }
    var retrievalRequest = new RetrievalRequest(
            command.indexNames(), rewriteResult.rewrittenQuery(),
            SearchMethod.fromValue(command.searchMethod()),
            command.vecDocsNum(), command.txtDocsNum(),
            command.vecScoreThreshold(), command.textScoreThreshold(),
            metadataFilters);
    RetrievalResult retrievalResult = retrievalPort.retrieve(retrievalRequest);

    // 3. Rerank
    List<RetrievedDocument> rerankedDocuments = rerankPort.rerank(
            rewriteResult.rewrittenQuery(),
            retrievalResult.rerankDocuments(),
            command.rerankScoreThreshold());

    if (rerankedDocuments.isEmpty()) {
        for (String indexName : command.indexNames()) {
            questionHistoryPort.recordQuestion(indexName, command.query());
        }
        return new QueryResult(NO_DOCS_FALLBACK, List.of(),
                retrievalResult.recallDocuments(), List.of(), List.of());
    }

    // 4. Source document selection (unchanged)
    List<RetrievedDocument> sourceDocuments = contextAssemblyService.selectSourceDocuments(
            retrievalResult, rerankedDocuments);

    // 5. Citation assembly + answer generation
    String answer;
    List<Citation> citations = List.of();
    if (ragProperties.isCitationEnabled()) {
        PromptWithCitations pwc = citationAssemblyService.assemble(rerankedDocuments);
        String rawAnswer = answerGenerationPort.generateAnswer(command.query(), pwc.formattedContext());
        CitedAnswer citedAnswer = citationAssemblyService.parseResponse(rawAnswer, pwc.citationMap());
        answer = citedAnswer.answer();
        citations = citedAnswer.citations();
    } else {
        // Fallback: build simple context without citation numbering
        String simpleContext = rerankedDocuments.stream()
                .map(RetrievedDocument::pageContent)
                .collect(Collectors.joining("\n"));
        answer = answerGenerationPort.generateAnswer(command.query(), simpleContext);
    }

    // 6. Record question history
    for (String indexName : command.indexNames()) {
        questionHistoryPort.recordQuestion(indexName, command.query());
    }

    return new QueryResult(answer, sourceDocuments,
            retrievalResult.recallDocuments(), rerankedDocuments, citations);
}
```

- [ ] **Step 3: Update `QueryResult` record (nested in the interface) to include citations**

**Important:** `QueryResult` is a nested record inside the `RagQueryApplicationService` interface (line 38). It has a compact constructor that calls `List.copyOf()` on list fields. The 5th field must follow the same pattern:

```java
record QueryResult(
        String answer,
        List<RetrievedDocument> sourceDocuments,
        List<RetrievedDocument> recallDocuments,
        List<RetrievedDocument> rerankDocuments,
        List<Citation> citations    // NEW
) {
    QueryResult {
        sourceDocuments = List.copyOf(sourceDocuments);
        recallDocuments = List.copyOf(recallDocuments);
        rerankDocuments = List.copyOf(rerankDocuments);
        citations = List.copyOf(citations);
    }
}
```

- [ ] **Step 4: Update RagController to map citations to CitationDto in response**

- [ ] **Step 5: Update RagQueryApplicationServiceTest**

- [ ] **Step 6: Run full test suite**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java \
       backend-java/src/main/java/com/huatai/rag/api/rag/ \
       backend-java/src/test/
git commit -m "feat: integrate query rewriting and citation into RAG pipeline"
```

---

### Task 14: End-to-End Smoke Test

- [ ] **Step 1: Run full test suite**

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
```
Expected: All tests PASS (existing 42 + ~15-20 new)

- [ ] **Step 2: Verify application starts**

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
```
Verify: starts on port 8001, no startup errors, `/health` returns 200

- [ ] **Step 3: Commit if any remaining fixes needed**

---

## Chunk 5: Offline Evaluation System

### Task 15: Evaluation Data Models

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/model/TestCase.java`
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/model/TestDataset.java`
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/model/ExpectedSource.java`
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/model/TraceRecord.java`
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/model/EvaluationReport.java`

- [ ] **Step 1: Create all evaluation model records**

```java
// TestCase.java
package com.huatai.rag.evaluation.model;

import com.huatai.rag.domain.rag.StructuredQuery;
import java.util.List;

public record TestCase(
        String id,
        String module,
        String query,
        List<String> expectedKeywords,
        StructuredQuery expectedStructured,
        List<ExpectedSource> expectedSources,
        String referenceAnswer,
        String difficulty
) {}

// TestDataset.java
public record TestDataset(String version, String dataset, List<TestCase> cases) {}

// ExpectedSource.java
public record ExpectedSource(String filename, List<Integer> pageNumbers) {}

// TraceRecord.java (see spec Section 5.4)

// EvaluationReport.java
public record EvaluationReport(
        String datasetName,
        int totalCases,
        Map<String, Double> aggregateMetrics,
        List<CaseResult> caseResults
) {
    public record CaseResult(String caseId, TraceRecord trace, Map<String, Double> metrics) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/evaluation/
git commit -m "feat: add evaluation data models"
```

---

### Task 16: EvaluationRunner + RagasClient

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/application/EvaluationRunner.java`
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/infrastructure/RagasClient.java`
- Create: `backend-java/src/main/java/com/huatai/rag/evaluation/application/ReportGenerator.java`
- Test: `backend-java/src/test/java/com/huatai/rag/evaluation/RagasClientTest.java`

- [ ] **Step 1: Write test for RagasClient (WireMock)**

```java
package com.huatai.rag.evaluation;

import com.huatai.rag.evaluation.infrastructure.RagasClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RagasClientTest {

    @Test
    void parses_evaluation_response() {
        // Test JSON parsing of RAGAS response format
        var client = new RagasClient("http://localhost:8002");
        // Use mocked HTTP or test JSON deserialization directly
        var json = """
                {"faithfulness": [0.85], "answer_relevancy": [0.90],
                 "context_precision": [0.75], "context_recall": [0.80]}""";
        var metrics = client.parseMetrics(json);
        assertThat(metrics.get("faithfulness")).isEqualTo(0.85);
    }
}
```

- [ ] **Step 2: Implement RagasClient**

Key details:
- Base URL configurable via `RAGAS_EVALUATOR_URL` env var (default `http://localhost:8002`)
- POST `/evaluate` with trace data in RAGAS format
- POST `/generate_testset` for synthetic test generation
- Uses `java.net.http.HttpClient`

- [ ] **Step 3: Implement EvaluationRunner**

Key details:
- Accepts `TestDataset`, iterates each `TestCase`
- Calls `RagQueryApplicationService.handle()` for each case
- Collects `TraceRecord` with timing data
- Computes custom metrics (Keyword Recall, Structured Parse Rate, Citation Accuracy)
- Calls `RagasClient` for RAGAS metrics
- Returns `EvaluationReport`

- [ ] **Step 4: Implement ReportGenerator**

Writes `EvaluationReport` to JSON file.

- [ ] **Step 5: Run tests**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/evaluation/ \
       backend-java/src/test/java/com/huatai/rag/evaluation/
git commit -m "feat: add evaluation runner, RAGAS client, and report generator"
```

---

### Task 17: RAGAS Python Sidecar

**Files:**
- Create: `ragas-evaluator/app.py`
- Create: `ragas-evaluator/requirements.txt`
- Create: `ragas-evaluator/Dockerfile`
- Create: `ragas-evaluator/docker-compose.yml`

- [ ] **Step 1: Create requirements.txt**

```
ragas>=0.2.0
fastapi>=0.104.0
uvicorn>=0.24.0
boto3>=1.34.0
datasets>=2.16.0
```

- [ ] **Step 2: Create app.py** (see spec Section 5.5)

Key endpoints:
- `GET /health` — health check
- `POST /evaluate` — accepts traces, returns RAGAS metrics
- `POST /generate_testset` — generates synthetic test set from documents

- [ ] **Step 3: Create Dockerfile**

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
EXPOSE 8002
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8002"]
```

- [ ] **Step 4: Create docker-compose.yml** (see spec Section 5.9)

- [ ] **Step 5: Test locally**

```bash
cd ragas-evaluator && docker-compose up -d
curl http://localhost:8002/health
```
Expected: `{"status": "ok"}`

- [ ] **Step 6: Commit**

```bash
git add ragas-evaluator/
git commit -m "feat: add RAGAS evaluation Python sidecar service"
```

---

### Task 18: Test Datasets + Evaluation Test Class

**Files:**
- Create: `backend-java/src/test/resources/evaluation/cob_testset.json`
- Create: `backend-java/src/test/resources/evaluation/collateral_testset.json`
- Create: `backend-java/src/test/java/com/huatai/rag/evaluation/RagEvaluationTest.java`

- [ ] **Step 1: Create skeleton test datasets**

Create `cob_testset.json` and `collateral_testset.json` with 2-3 placeholder cases each (structure per spec Section 5.2). These will be filled in by the business team + RAGAS generation later.

- [ ] **Step 2: Create RagEvaluationTest**

```java
package com.huatai.rag.evaluation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@Tag("evaluation")
class RagEvaluationTest {

    @Autowired EvaluationRunner runner;

    @Test
    void evaluateCobDataset() {
        var dataset = TestDatasetLoader.load("evaluation/cob_testset.json");
        var report = runner.run(dataset);
        report.writeTo("evaluation-results/cob-report.json");
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/test/resources/evaluation/ \
       backend-java/src/test/java/com/huatai/rag/evaluation/RagEvaluationTest.java
git commit -m "feat: add evaluation test datasets and test runner class"
```

---

## Chunk 6: Final Verification + Cleanup

### Task 19: Full Verification

- [ ] **Step 1: Run complete test suite**

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
```
Expected: All tests PASS (original 42 + ~15-20 new = ~57-62 total)

- [ ] **Step 2: Verify application starts cleanly**

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
```
Check: no startup errors, `/health` returns 200

- [ ] **Step 3: Verify RAGAS sidecar**

```bash
cd ragas-evaluator && docker-compose up -d
curl http://localhost:8002/health
```

- [ ] **Step 4: Final commit if any fixes needed**

---

### Task 20: Update Control Documents

- [ ] **Step 1: Update CLAUDE.md**

Add to "Current Project State" section:
- Batch A (Enhanced RAG) implemented: query rewriting, answer citation, offline evaluation
- RAGAS Python sidecar at `ragas-evaluator/`
- Feature flags: `rag.query-rewrite.enabled`, `rag.citation.enabled`

- [ ] **Step 2: Update control/Documentation.md**

Record Batch A completion, next steps (Batch B).

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md control/Documentation.md
git commit -m "docs: update control documents for Batch A completion"
```
