# Spring Boot RAG Backend Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current Python FastAPI RAG backend with a Spring Boot + Spring AI backend that keeps frontend behavior unchanged and preserves all AWS OpenSearch, Bedrock, and document-processing capabilities without downgrade.

**Architecture:** Build a single Spring Boot service with strong internal boundaries for API compatibility, ingestion, parser SPI, retrieval, generation, and history/state management. Use AWS BDA as the target parser implementation from day one, keep OpenSearch and Bedrock as the managed core, and preserve contract parity through DTO- and flow-level regression tests.

**Tech Stack:** Java 21, Spring Boot, Spring AI, Spring Web, Spring Validation, Spring Data JPA, Flyway, PostgreSQL, AWS SDK v2, OpenSearch Java client, Maven, JUnit 5, Mockito, Testcontainers, WireMock

---
## Proposed File Structure

### New backend project

- `backend-java/pom.xml`
  Responsibility: Maven dependency and plugin management.
- `backend-java/README.md`
  Responsibility: local development, configuration, and run instructions.
- `backend-java/src/main/java/com/huatai/rag/RagApplication.java`
  Responsibility: Spring Boot bootstrap.

### Configuration and infrastructure

- `backend-java/src/main/resources/application.yml`
  Responsibility: common runtime defaults.
- `backend-java/src/main/resources/application-local.yml`
  Responsibility: local development overrides.
- `backend-java/src/main/resources/application-test.yml`
  Responsibility: test profile settings.
- `backend-java/src/main/java/com/huatai/rag/config/AwsProperties.java`
  Responsibility: Bedrock and BDA region/model configuration.
- `backend-java/src/main/java/com/huatai/rag/config/OpenSearchProperties.java`
  Responsibility: OpenSearch endpoint and auth configuration.
- `backend-java/src/main/java/com/huatai/rag/config/StorageProperties.java`
  Responsibility: upload path and storage mode.
- `backend-java/src/main/java/com/huatai/rag/config/RagProperties.java`
  Responsibility: chunking, thresholds, timeout, retry settings.
- `backend-java/src/main/java/com/huatai/rag/config/ClientConfig.java`
  Responsibility: AWS SDK, OpenSearch, and Spring AI client beans.
- `backend-java/src/main/java/com/huatai/rag/support/RequestCorrelationFilter.java`
  Responsibility: correlation id per request.
- `backend-java/src/main/java/com/huatai/rag/support/RetryUtils.java`
  Responsibility: bounded retry logic for AWS/OpenSearch calls.
- `backend-java/src/main/java/com/huatai/rag/support/FileStorageService.java`
  Responsibility: file save/load abstraction.

### API compatibility layer

- `backend-java/src/main/java/com/huatai/rag/api/controller/HealthController.java`
  Responsibility: `/health`.
- `backend-java/src/main/java/com/huatai/rag/api/controller/RagController.java`
  Responsibility: `/rag_answer`.
- `backend-java/src/main/java/com/huatai/rag/api/controller/UploadController.java`
  Responsibility: `/upload_files`, `/processed_files`, `/get_index/{filename}`.
- `backend-java/src/main/java/com/huatai/rag/api/controller/QuestionController.java`
  Responsibility: `/top_questions/{indexName}` and `/top_questions_multi`.
- `backend-java/src/main/java/com/huatai/rag/api/dto/RagRequest.java`
  Responsibility: request DTO compatibility.
- `backend-java/src/main/java/com/huatai/rag/api/dto/RagResponse.java`
  Responsibility: answer DTO compatibility.
- `backend-java/src/main/java/com/huatai/rag/api/dto/SourceDocumentDto.java`
  Responsibility: source/rerank document compatibility.
- `backend-java/src/main/java/com/huatai/rag/api/dto/RecallDocumentDto.java`
  Responsibility: recall document compatibility.
- `backend-java/src/main/java/com/huatai/rag/api/dto/ProcessedFilesResponse.java`
  Responsibility: processed files response compatibility.
- `backend-java/src/main/java/com/huatai/rag/api/dto/TopQuestionsResponse.java`
  Responsibility: top-question response compatibility.
- `backend-java/src/main/java/com/huatai/rag/api/error/ApiExceptionHandler.java`
  Responsibility: frontend-safe exception mapping.

### Persistence

- `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentFile.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionJob.java`
- `backend-java/src/main/java/com/huatai/rag/domain/history/QuestionHistory.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentFileRepository.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionJobRepository.java`
- `backend-java/src/main/java/com/huatai/rag/domain/history/QuestionHistoryRepository.java`
- `backend-java/src/main/resources/db/migration/V1__initial_schema.sql`
- `backend-java/src/main/resources/db/migration/V2__history_indexes.sql`

### Parser SPI and normalization

- `backend-java/src/main/java/com/huatai/rag/parser/DocumentParser.java`
- `backend-java/src/main/java/com/huatai/rag/parser/model/ParserRequest.java`
- `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedDocument.java`
- `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedPage.java`
- `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedChunk.java`
- `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedAsset.java`
- `backend-java/src/main/java/com/huatai/rag/parser/bda/BdaClient.java`
- `backend-java/src/main/java/com/huatai/rag/parser/bda/BdaDocumentParser.java`
- `backend-java/src/main/java/com/huatai/rag/parser/bda/BdaResultMapper.java`
- `backend-java/src/main/java/com/huatai/rag/parser/ChunkNormalizationService.java`

### Retrieval and generation

- `backend-java/src/main/java/com/huatai/rag/index/IndexNamingStrategy.java`
- `backend-java/src/main/java/com/huatai/rag/index/OpenSearchIndexManager.java`
- `backend-java/src/main/java/com/huatai/rag/index/OpenSearchDocumentWriter.java`
- `backend-java/src/main/java/com/huatai/rag/index/model/IndexedChunk.java`
- `backend-java/src/main/java/com/huatai/rag/retrieval/OpenSearchRetrievalService.java`
- `backend-java/src/main/java/com/huatai/rag/retrieval/RerankService.java`
- `backend-java/src/main/java/com/huatai/rag/retrieval/ContextAssembler.java`
- `backend-java/src/main/java/com/huatai/rag/retrieval/model/RetrievedDocument.java`
- `backend-java/src/main/java/com/huatai/rag/retrieval/model/RetrievalResult.java`
- `backend-java/src/main/java/com/huatai/rag/bedrock/EmbeddingService.java`
- `backend-java/src/main/java/com/huatai/rag/bedrock/BedrockEmbeddingService.java`
- `backend-java/src/main/java/com/huatai/rag/bedrock/AnswerGenerationService.java`
- `backend-java/src/main/java/com/huatai/rag/bedrock/BedrockAnswerGenerationService.java`
- `backend-java/src/main/java/com/huatai/rag/bedrock/PromptTemplateService.java`
- `backend-java/src/main/java/com/huatai/rag/service/RagQueryService.java`
- `backend-java/src/main/java/com/huatai/rag/service/IngestionService.java`
- `backend-java/src/main/java/com/huatai/rag/service/ProcessedFileService.java`
- `backend-java/src/main/java/com/huatai/rag/service/QuestionHistoryService.java`

### Tests and fixtures

- `backend-java/src/test/java/com/huatai/rag/api/RagControllerContractTest.java`
- `backend-java/src/test/java/com/huatai/rag/api/UploadControllerContractTest.java`
- `backend-java/src/test/java/com/huatai/rag/api/QuestionControllerContractTest.java`
- `backend-java/src/test/java/com/huatai/rag/index/IndexNamingStrategyTest.java`
- `backend-java/src/test/java/com/huatai/rag/parser/BdaResultMapperTest.java`
- `backend-java/src/test/java/com/huatai/rag/retrieval/OpenSearchRetrievalServiceTest.java`
- `backend-java/src/test/java/com/huatai/rag/service/RagQueryServiceTest.java`
- `backend-java/src/test/java/com/huatai/rag/service/IngestionServiceTest.java`
- `backend-java/src/test/java/com/huatai/rag/integration/PostgresIntegrationTest.java`
- `backend-java/src/test/java/com/huatai/rag/integration/OpenSearchIntegrationTest.java`
- `backend-java/src/test/java/com/huatai/rag/regression/RagRegressionTest.java`
- `backend-java/src/test/resources/fixtures/contracts/`
- `backend-java/src/test/resources/fixtures/parser/`
- `backend-java/src/test/resources/fixtures/regression/`

### Existing files to reference during migration

- `api/api.py`
- `api/RAG_System.py`
- `api/document_processing.py`
- `api/opensearch_search.py`
- `api/embedding_model.py`
- `api/llm_processor.py`
- `api/processed_files.txt`

---
### Task 1: Freeze Baseline Contracts and Regression Data

**Files:**
- Create: `backend-java/src/test/resources/fixtures/contracts/rag-answer-request.json`
- Create: `backend-java/src/test/resources/fixtures/contracts/rag-answer-response.json`
- Create: `backend-java/src/test/resources/fixtures/contracts/processed-files-response.json`
- Create: `backend-java/src/test/resources/fixtures/contracts/top-questions-response.json`
- Create: `backend-java/src/test/resources/fixtures/regression/questions.csv`
- Create: `backend-java/src/test/resources/fixtures/regression/documents/.gitkeep`
- Create: `docs/superpowers/plans/migration-baseline-checklist.md`
- Reference: `api/api.py`
- Reference: `api/RAG_System.py`
- Reference: `api/document_processing.py`
- Reference: `api/opensearch_search.py`

- [ ] **Step 1: Record current endpoint contracts into fixtures**

```json
{
  "session_id": "web-session",
  "index_names": ["a3f2c1b8"],
  "query": "TCL科技1Q25的营收情况如何？",
  "module": "RAG",
  "vec_docs_num": 3,
  "txt_docs_num": 3,
  "vec_score_threshold": 0.0,
  "text_score_threshold": 0.0,
  "rerank_score_threshold": 0.5,
  "search_method": "mix"
}
```

- [ ] **Step 2: Write a baseline checklist of behaviors to preserve**

Include:
- multi-index query joining
- vector/text/mix retrieval modes
- recall/rerank/source document response fields
- deterministic index naming
- processed-file listing semantics
- top-questions aggregation semantics

- [ ] **Step 3: Capture representative PDFs and golden questions**

```csv
document,index_name,question,expected_keywords,min_sources
tcl-report.pdf,a3f2c1b8,TCL科技1Q25的营收情况如何？,"营收|同比|季度",1
```

- [ ] **Step 4: Verify baseline assets exist**

Run: `Get-ChildItem backend-java\src\test\resources\fixtures -Recurse`
Expected: contract JSON, regression CSV, and document fixture directory are present.

- [ ] **Step 5: Commit the baseline package**

```bash
git add backend-java/src/test/resources docs/superpowers/plans/migration-baseline-checklist.md
git commit -m "test: add backend migration baseline fixtures"
```

### Task 2: Bootstrap the Spring Boot Project

**Files:**
- Create: `backend-java/pom.xml`
- Create: `backend-java/README.md`
- Create: `backend-java/src/main/java/com/huatai/rag/RagApplication.java`
- Create: `backend-java/src/main/resources/application.yml`
- Create: `backend-java/src/main/resources/application-local.yml`
- Create: `backend-java/src/main/resources/application-test.yml`
- Create: `backend-java/src/test/java/com/huatai/rag/RagApplicationTest.java`

- [ ] **Step 1: Write the failing bootstrap test**

```java
@SpringBootTest
class RagApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the bootstrap test to verify it fails before setup**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test`
Expected: FAIL because the Maven project does not yet exist.

- [ ] **Step 3: Create the Maven project and root application**

```java
@SpringBootApplication
public class RagApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
```

Dependencies must include:
- spring-boot-starter-web
- spring-boot-starter-validation
- spring-boot-starter-data-jpa
- flyway-core
- postgresql
- spring-ai-bedrock starter
- aws sdk v2 modules
- opensearch-java
- junit 5, mockito, testcontainers, wiremock

- [ ] **Step 4: Add basic profiles and port compatibility**

Set service port to `8001` for frontend compatibility.

- [ ] **Step 5: Run the bootstrap test to verify the project starts**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test`
Expected: PASS.

- [ ] **Step 6: Commit the project skeleton**

```bash
git add backend-java/pom.xml backend-java/README.md backend-java/src/main backend-java/src/test/java/com/huatai/rag/RagApplicationTest.java
git commit -m "build: bootstrap spring boot rag backend"
```

### Task 3: Add Configuration, Persistence, and Flyway Migrations

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/config/AwsProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/config/OpenSearchProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/config/StorageProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/config/RagProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/config/ClientConfig.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentFile.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionJob.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/history/QuestionHistory.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentFileRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionJobRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/history/QuestionHistoryRepository.java`
- Create: `backend-java/src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `backend-java/src/main/resources/db/migration/V2__history_indexes.sql`
- Create: `backend-java/src/test/java/com/huatai/rag/integration/PostgresIntegrationTest.java`

- [ ] **Step 1: Write the failing repository integration test**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PostgresIntegrationTest {
    @Test
    void flywayCreatesDocumentAndHistoryTables() {
    }
}
```

- [ ] **Step 2: Run the repository integration test to verify it fails**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PostgresIntegrationTest test`
Expected: FAIL because entities and migrations do not yet exist.

- [ ] **Step 3: Implement `@ConfigurationProperties` classes and client beans**

Externalize:
- AWS credentials/regions/model ids
- OpenSearch endpoint and auth
- storage path
- chunking, retry, timeout settings

- [ ] **Step 4: Implement entities, repositories, and Flyway schema**

Tables must include:
- `document_file`
- `ingestion_job`
- `question_history`

- [ ] **Step 5: Run the repository integration test to verify schema creation**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PostgresIntegrationTest test`
Expected: PASS.

- [ ] **Step 6: Commit the persistence foundation**

```bash
git add backend-java/src/main/java/com/huatai/rag/config backend-java/src/main/java/com/huatai/rag/domain backend-java/src/main/resources/db backend-java/src/test/java/com/huatai/rag/integration/PostgresIntegrationTest.java
git commit -m "feat: add backend configuration and persistence foundation"
```

### Task 4: Build the API Compatibility Layer

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/api/controller/HealthController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/controller/RagController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/controller/UploadController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/controller/QuestionController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/dto/RagRequest.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/dto/RagResponse.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/dto/SourceDocumentDto.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/dto/RecallDocumentDto.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/dto/ProcessedFilesResponse.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/dto/TopQuestionsResponse.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/error/ApiExceptionHandler.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/RagControllerContractTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/UploadControllerContractTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/QuestionControllerContractTest.java`

- [ ] **Step 1: Write failing MockMvc contract tests from fixtures**

```java
@WebMvcTest(RagController.class)
class RagControllerContractTest {
    @Test
    void ragAnswerResponseMatchesContract() {
    }
}
```

- [ ] **Step 2: Run the controller contract tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest test`
Expected: FAIL because controllers and DTOs do not yet exist.

- [ ] **Step 3: Implement DTOs with Python-compatible field names**

Preserve:
- `index_names`
- `vec_docs_num`
- `text_score_threshold`
- `source_documents`
- `recall_documents`
- `rerank_documents`

- [ ] **Step 4: Implement controllers with placeholder service wiring**

Return contract-shaped responses first so tests can pass independently from retrieval logic.

- [ ] **Step 5: Run the controller contract tests to verify parity**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest test`
Expected: PASS.

- [ ] **Step 6: Commit the compatibility layer**

```bash
git add backend-java/src/main/java/com/huatai/rag/api backend-java/src/test/java/com/huatai/rag/api
git commit -m "feat: add api compatibility layer for rag backend"
```
### Task 5: Implement Index Naming and OpenSearch Index Management

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/index/IndexNamingStrategy.java`
- Create: `backend-java/src/main/java/com/huatai/rag/index/OpenSearchIndexManager.java`
- Create: `backend-java/src/main/java/com/huatai/rag/index/model/IndexedChunk.java`
- Create: `backend-java/src/test/java/com/huatai/rag/index/IndexNamingStrategyTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/integration/OpenSearchIntegrationTest.java`

- [ ] **Step 1: Write the failing index naming parity test**

```java
class IndexNamingStrategyTest {
    @Test
    void usesMd5FirstEightCharsForFilenameCompatibility() {
    }
}
```

- [ ] **Step 2: Run the naming test to verify it fails**

Run: `mvn -f backend-java/pom.xml -q -Dtest=IndexNamingStrategyTest test`
Expected: FAIL because the strategy does not yet exist.

- [ ] **Step 3: Implement deterministic index naming**

Preserve the Python compatibility rule unless a reviewed migration is intentionally introduced.

- [ ] **Step 4: Write the failing OpenSearch integration test for mapping creation**

Verify created mappings contain:
- `sentence_vector`
- `paragraph`
- `sentence`
- `metadata.*`

- [ ] **Step 5: Implement index creation and compatibility mapping**

Use explicit OpenSearch Java client calls. Do not hide field names behind generic abstractions.

- [ ] **Step 6: Run the naming and OpenSearch tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=IndexNamingStrategyTest,OpenSearchIntegrationTest test`
Expected: PASS.

- [ ] **Step 7: Commit index support**

```bash
git add backend-java/src/main/java/com/huatai/rag/index backend-java/src/test/java/com/huatai/rag/index backend-java/src/test/java/com/huatai/rag/integration/OpenSearchIntegrationTest.java
git commit -m "feat: add opensearch index compatibility support"
```

### Task 6: Implement Bedrock Embedding, Rerank, and Answer Services

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/bedrock/EmbeddingService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/bedrock/BedrockEmbeddingService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/retrieval/RerankService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/bedrock/AnswerGenerationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/bedrock/BedrockAnswerGenerationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/bedrock/PromptTemplateService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/support/RetryUtils.java`
- Create: `backend-java/src/test/java/com/huatai/rag/service/PromptTemplateServiceTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/service/BedrockClientWiringTest.java`

- [ ] **Step 1: Write the failing prompt-template test**

```java
class PromptTemplateServiceTest {
    @Test
    void buildsSystemAndUserPromptsWithCurrentContractLanguage() {
    }
}
```

- [ ] **Step 2: Run the prompt-template test to verify it fails**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PromptTemplateServiceTest test`
Expected: FAIL because the prompt service does not yet exist.

- [ ] **Step 3: Implement prompt generation and service contracts**

Preserve Python semantics:
- domain-expert system prompt
- direct-answer user instruction
- context-first answer generation

- [ ] **Step 4: Implement Bedrock clients with bounded retry behavior**

Support:
- embedding calls
- rerank calls
- answer-generation calls

- [ ] **Step 5: Add a bean wiring test with mocks or stubs**

Verify services can be instantiated without real AWS traffic.

- [ ] **Step 6: Run the prompt and wiring tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PromptTemplateServiceTest,BedrockClientWiringTest test`
Expected: PASS.

- [ ] **Step 7: Commit Bedrock services**

```bash
git add backend-java/src/main/java/com/huatai/rag/bedrock backend-java/src/main/java/com/huatai/rag/retrieval/RerankService.java backend-java/src/main/java/com/huatai/rag/support/RetryUtils.java backend-java/src/test/java/com/huatai/rag/service
git commit -m "feat: add bedrock embedding rerank and answer services"
```

### Task 7: Implement Retrieval, Mixed Search, and Context Assembly

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/retrieval/OpenSearchRetrievalService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/retrieval/ContextAssembler.java`
- Create: `backend-java/src/main/java/com/huatai/rag/retrieval/model/RetrievedDocument.java`
- Create: `backend-java/src/main/java/com/huatai/rag/retrieval/model/RetrievalResult.java`
- Create: `backend-java/src/test/java/com/huatai/rag/retrieval/OpenSearchRetrievalServiceTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/retrieval/ContextAssemblerTest.java`

- [ ] **Step 1: Write failing retrieval tests**

Cover:
- vector mode
- text mode
- mix mode
- deduplication
- thresholding
- rerank sorting

- [ ] **Step 2: Run the retrieval tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=OpenSearchRetrievalServiceTest,ContextAssemblerTest test`
Expected: FAIL because retrieval classes do not yet exist.

- [ ] **Step 3: Implement retrieval models and OpenSearch query behavior**

Reproduce the Python flow:
- embed query
- run kNN
- optionally run BM25
- merge and dedupe
- rerank and threshold

- [ ] **Step 4: Implement context assembly**

Preserve text-plus-asset structure so future multimodal prompting can reuse the same output.

- [ ] **Step 5: Run the retrieval tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=OpenSearchRetrievalServiceTest,ContextAssemblerTest test`
Expected: PASS.

- [ ] **Step 6: Commit retrieval flow**

```bash
git add backend-java/src/main/java/com/huatai/rag/retrieval backend-java/src/test/java/com/huatai/rag/retrieval
git commit -m "feat: implement opensearch retrieval and context assembly"
```

### Task 8: Implement `RagQueryService` and Wire `/rag_answer`

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/service/RagQueryService.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/controller/RagController.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/api/RagControllerContractTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/service/RagQueryServiceTest.java`

- [ ] **Step 1: Write the failing orchestration test**

```java
class RagQueryServiceTest {
    @Test
    void returnsAnswerRecallAndRerankDocumentsForRagMode() {
    }
}
```

- [ ] **Step 2: Run the orchestration and contract tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagQueryServiceTest,RagControllerContractTest test`
Expected: FAIL because the real orchestration path is not wired.

- [ ] **Step 3: Implement `RagQueryService`**

Flow:
- join multi-index input
- call retrieval
- assemble context
- call answer generation
- record question history
- map to compatible DTOs

- [ ] **Step 4: Replace placeholder controller responses with live orchestration**

- [ ] **Step 5: Run the orchestration and contract tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagQueryServiceTest,RagControllerContractTest test`
Expected: PASS.

- [ ] **Step 6: Commit `/rag_answer` implementation**

```bash
git add backend-java/src/main/java/com/huatai/rag/service/RagQueryService.java backend-java/src/main/java/com/huatai/rag/api/controller/RagController.java backend-java/src/test/java/com/huatai/rag/service/RagQueryServiceTest.java backend-java/src/test/java/com/huatai/rag/api/RagControllerContractTest.java
git commit -m "feat: implement rag answer orchestration"
```
### Task 9: Implement Parser SPI, BDA Mapping, and Chunk Normalization

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/parser/DocumentParser.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/model/ParserRequest.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedDocument.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedPage.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedChunk.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/model/ParsedAsset.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/bda/BdaClient.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/bda/BdaDocumentParser.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/bda/BdaResultMapper.java`
- Create: `backend-java/src/main/java/com/huatai/rag/parser/ChunkNormalizationService.java`
- Create: `backend-java/src/test/java/com/huatai/rag/parser/BdaResultMapperTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/parser/ChunkNormalizationServiceTest.java`
- Create: `backend-java/src/test/resources/fixtures/parser/bda-sample-response.json`

- [ ] **Step 1: Write failing BDA mapping and normalization tests**

Cover:
- page extraction
- section lineage
- text chunks
- asset references
- parser provenance

- [ ] **Step 2: Run the parser tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=BdaResultMapperTest,ChunkNormalizationServiceTest test`
Expected: FAIL because the parser SPI and mapper do not yet exist.

- [ ] **Step 3: Implement parser contracts first, then the BDA client**

- [ ] **Step 4: Implement BDA result mapping and chunk normalization**

Normalized output must preserve:
- `pageNumber`
- `sectionPath`
- `paragraph`
- `sentence`
- `assetRefs`
- `parserType`

- [ ] **Step 5: Run the parser tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=BdaResultMapperTest,ChunkNormalizationServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit parser SPI and BDA integration**

```bash
git add backend-java/src/main/java/com/huatai/rag/parser backend-java/src/test/java/com/huatai/rag/parser backend-java/src/test/resources/fixtures/parser/bda-sample-response.json
git commit -m "feat: add parser spi and bda document mapping"
```

### Task 10: Implement File Storage, Ingestion, Indexing, and Upload Endpoints

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/support/FileStorageService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/service/IngestionService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/service/ProcessedFileService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/index/OpenSearchDocumentWriter.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/index/OpenSearchIndexManager.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/controller/UploadController.java`
- Create: `backend-java/src/test/java/com/huatai/rag/service/IngestionServiceTest.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/api/UploadControllerContractTest.java`

- [ ] **Step 1: Write failing ingestion and upload contract tests**

Cover:
- file save
- deterministic index assignment
- parser invocation
- embedding and bulk-ingest handoff
- processed-file lookup

- [ ] **Step 2: Run the ingestion tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=IngestionServiceTest,UploadControllerContractTest test`
Expected: FAIL because the ingestion pipeline is not yet wired.

- [ ] **Step 3: Implement file storage and ingestion orchestration**

Flow:
- save file
- persist document record and ingestion job
- compute index name
- parse through `DocumentParser`
- normalize chunks
- embed sentences
- create index if needed
- bulk write records
- mark ingestion complete

- [ ] **Step 4: Implement processed-file queries and filename-to-index lookup**

Behavior must stay compatible with the React frontend.

- [ ] **Step 5: Run the ingestion and upload contract tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=IngestionServiceTest,UploadControllerContractTest test`
Expected: PASS.

- [ ] **Step 6: Commit ingestion and upload flow**

```bash
git add backend-java/src/main/java/com/huatai/rag/support/FileStorageService.java backend-java/src/main/java/com/huatai/rag/service/IngestionService.java backend-java/src/main/java/com/huatai/rag/service/ProcessedFileService.java backend-java/src/main/java/com/huatai/rag/index/OpenSearchDocumentWriter.java backend-java/src/main/java/com/huatai/rag/api/controller/UploadController.java backend-java/src/test/java/com/huatai/rag/service/IngestionServiceTest.java backend-java/src/test/java/com/huatai/rag/api/UploadControllerContractTest.java
git commit -m "feat: implement document ingestion and upload endpoints"
```

### Task 11: Implement Question History Endpoints

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/service/QuestionHistoryService.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/controller/QuestionController.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/api/QuestionControllerContractTest.java`

- [ ] **Step 1: Write the failing history tests**

Add cases for:
- save question per index
- aggregate top N per index
- aggregate top N across multiple indices

- [ ] **Step 2: Run the question-history tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=QuestionControllerContractTest test`
Expected: FAIL because the history service is not yet implemented.

- [ ] **Step 3: Implement `QuestionHistoryService` and controller wiring**

Preserve response shape:

```json
{
  "status": "success",
  "questions": [
    { "question": "示例问题", "count": 3 }
  ]
}
```

- [ ] **Step 4: Run the question-history tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=QuestionControllerContractTest test`
Expected: PASS.

- [ ] **Step 5: Commit history support**

```bash
git add backend-java/src/main/java/com/huatai/rag/service/QuestionHistoryService.java backend-java/src/main/java/com/huatai/rag/api/controller/QuestionController.java backend-java/src/test/java/com/huatai/rag/api/QuestionControllerContractTest.java
git commit -m "feat: add question history endpoints"
```

### Task 12: Add Observability, Error Mapping, and Cutover Verification

**Files:**
- Create: `backend-java/src/test/java/com/huatai/rag/api/ApiExceptionHandlerTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/regression/RagRegressionTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/regression/IngestionRegressionTest.java`
- Create: `docs/superpowers/plans/migration-cutover-checklist.md`
- Modify: `backend-java/src/main/java/com/huatai/rag/support/RequestCorrelationFilter.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/error/ApiExceptionHandler.java`
- Modify: `backend-java/README.md`

- [ ] **Step 1: Write failing observability and regression tests**

Cover:
- correlation id propagation
- validation and external-service error mapping
- ingestion success on representative PDFs
- retrieval overlap expectations
- answer keyword expectations

- [ ] **Step 2: Run the observability and regression tests to verify they fail**

Run: `mvn -f backend-java/pom.xml -q -Dtest=ApiExceptionHandlerTest,RagRegressionTest,IngestionRegressionTest test`
Expected: FAIL until all glue code and parity gaps are fixed.

- [ ] **Step 3: Implement missing observability/error rules and close parity gaps**

Ensure OpenSearch, Bedrock, and BDA failures do not leak raw internals to the frontend.

- [ ] **Step 4: Run the complete test suite**

Run: `mvn -f backend-java/pom.xml test`
Expected: PASS for unit, contract, integration, and regression tests.

- [ ] **Step 5: Smoke test the service locally**

```bash
mvn -f backend-java/pom.xml spring-boot:run
curl http://localhost:8001/health
```

Expected: service starts on port `8001` and `/health` returns success.

- [ ] **Step 6: Verify frontend compatibility against the Java backend**

Point the React frontend to the Java backend and verify:
- upload flow works
- processed file list renders
- Q&A works
- source documents display correctly

- [ ] **Step 7: Write the cutover checklist**

Include:
- env var preparation
- DB migration applied
- OpenSearch connectivity verified
- Bedrock model access verified
- BDA parsing verified
- frontend environment pointed to Java backend
- rollback rule defined

- [ ] **Step 8: Commit regression coverage and cutover checklist**

```bash
git add backend-java/src/test/java/com/huatai/rag/api/ApiExceptionHandlerTest.java backend-java/src/test/java/com/huatai/rag/regression docs/superpowers/plans/migration-cutover-checklist.md backend-java/README.md
git commit -m "test: add migration regression and cutover checklist"
```

## Implementation Notes

- Keep the existing Python backend available as a comparison target until Task 12 passes.
- Do not change React endpoint names or field names during implementation.
- Keep retrieval logic explicit; do not bury kNN/BM25/rerank behavior behind opaque abstractions.
- Treat parser normalization as the highest-value boundary. Future parser changes should stop there.
- Preserve deterministic index naming unless a reviewed migration explicitly changes it.
- If BDA output differs from Docling in chunk boundaries, normalize toward stable retrieval quality rather than line-by-line similarity.

## Verification Commands Summary

```bash
mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test
mvn -f backend-java/pom.xml -q -Dtest=PostgresIntegrationTest test
mvn -f backend-java/pom.xml -q -Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest test
mvn -f backend-java/pom.xml -q -Dtest=IndexNamingStrategyTest,OpenSearchIntegrationTest test
mvn -f backend-java/pom.xml -q -Dtest=PromptTemplateServiceTest,BedrockClientWiringTest test
mvn -f backend-java/pom.xml -q -Dtest=OpenSearchRetrievalServiceTest,ContextAssemblerTest test
mvn -f backend-java/pom.xml -q -Dtest=RagQueryServiceTest,RagControllerContractTest test
mvn -f backend-java/pom.xml -q -Dtest=BdaResultMapperTest,ChunkNormalizationServiceTest test
mvn -f backend-java/pom.xml -q -Dtest=IngestionServiceTest,UploadControllerContractTest test
mvn -f backend-java/pom.xml -q -Dtest=QuestionControllerContractTest test
mvn -f backend-java/pom.xml -q -Dtest=ApiExceptionHandlerTest,RagRegressionTest,IngestionRegressionTest test
mvn -f backend-java/pom.xml test
```

## Definition of Done

The migration is done only when all of the following are true:

- The Java backend exposes all current frontend-required endpoints.
- Upload, processed-file lookup, and RAG answer flows work end-to-end.
- OpenSearch vector, text, and mixed retrieval are preserved.
- Bedrock embedding, rerank, and answer generation are preserved.
- BDA parsing is integrated behind the parser SPI.
- Question history endpoints are preserved.
- Regression tests pass on representative documents and questions.
- The React frontend runs against the Java backend without code-level API rewrites.
