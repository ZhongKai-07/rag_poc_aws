# Spring Boot RAG Backend Migration Layered Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java backend under `backend-java/` that fully replaces the current Python FastAPI RAG backend while keeping the React frontend unchanged and preserving all current AWS OpenSearch, Bedrock, ingestion, retrieval, rerank, and Q&A capabilities.

**Architecture:** Use a layered architecture with `api`, `application`, `domain`, and `infrastructure` as top-level boundaries. Organize each major business capability inside those layers so external integrations remain isolated in infrastructure, orchestration remains in application, and core contracts and models remain in domain.

**Tech Stack:** Java 21, Spring Boot, Spring AI, Spring Web, Spring Validation, Spring Data JPA, Flyway, PostgreSQL, AWS SDK v2, OpenSearch Java client, Maven, JUnit 5, Mockito, Testcontainers, WireMock

---

## Layered Architecture Mapping

| Current plan area | New layered location | Responsibility |
| --- | --- | --- |
| `api/controller`, `api/dto`, `api/error` | `api` | HTTP contract compatibility, request validation, response mapping |
| `service/*` | `application` | Use-case orchestration across parsing, indexing, retrieval, generation, and history |
| `domain/document`, `domain/history`, parser/retrieval models, naming rules | `domain` | Core models, enums, value objects, business rules, ports/interfaces |
| `bedrock/*` | `infrastructure/bedrock` | Bedrock embedding, rerank, and answer generation adapters |
| `index/*`, `retrieval/*` OpenSearch-specific pieces | `infrastructure/opensearch` | OpenSearch index management, search queries, bulk ingestion |
| `parser/bda/*` | `infrastructure/bda` | AWS BDA client and response mapping |
| persistence repositories/entities | `infrastructure/persistence` | JPA entities, Spring Data repositories, schema migrations |
| file storage and support utilities | `infrastructure/storage`, `infrastructure/support` | Local file persistence, request correlation, retries |

## Target Package Structure

```text
backend-java/
  pom.xml
  README.md
  src/main/java/com/huatai/rag/
    RagApplication.java
    api/
      health/
      rag/
      upload/
      question/
      common/
    application/
      rag/
      ingestion/
      registry/
      history/
      common/
    domain/
      rag/
      document/
      parser/
      retrieval/
      history/
      common/
    infrastructure/
      config/
      bedrock/
      opensearch/
      bda/
      persistence/
      storage/
      support/
  src/main/resources/
    application.yml
    application-local.yml
    application-test.yml
    db/migration/
  src/test/java/com/huatai/rag/
    api/
    application/
    domain/
    infrastructure/
    integration/
    regression/
  src/test/resources/fixtures/
    contracts/
    parser/
    regression/
```

## Detailed Directory Responsibilities

### `api`

- Owns controllers, transport DTOs, request validation, and exception translation.
- Must preserve the current frontend-facing endpoints exactly:
  - `POST /upload_files`
  - `POST /rag_answer`
  - `GET /processed_files`
  - `GET /get_index/{filename}`
  - `GET /top_questions/{index_name}`
  - `GET /top_questions_multi`
  - `GET /health`
- Must not contain AWS SDK, OpenSearch DSL, or persistence logic.

### `application`

- Owns use-case orchestration only.
- Calls domain ports and coordinates multi-step flows.
- Contains transaction boundaries where needed.
- Example services:
  - `RagQueryApplicationService`
  - `DocumentIngestionApplicationService`
  - `ProcessedFileQueryApplicationService`
  - `QuestionHistoryApplicationService`

### `domain`

- Owns core models, policies, business ports, value objects, and invariants.
- Does not depend on Spring MVC, JPA, AWS SDK, or OpenSearch client classes.
- Typical contents:
  - `ParsedDocument`, `ParsedChunk`, `ParsedAsset`
  - `RetrievedDocument`, `RetrievalResult`
  - `DocumentFileRecord`, `IngestionStatus`, `SearchMethod`
  - `IndexNamingPolicy`
  - ports such as `DocumentParser`, `EmbeddingPort`, `RetrievalPort`, `RerankPort`, `AnswerGenerationPort`, `DocumentRegistryPort`, `QuestionHistoryPort`

### `infrastructure`

- Owns all external system integrations.
- Adapts Bedrock, BDA, OpenSearch, PostgreSQL, and file system concerns to domain ports.
- Holds configuration beans, JPA entities/repositories, client wiring, and support utilities.

---

## File Mapping Table

### API layer

- `backend-java/src/main/java/com/huatai/rag/api/health/HealthController.java`
- `backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java`
- `backend-java/src/main/java/com/huatai/rag/api/upload/UploadController.java`
- `backend-java/src/main/java/com/huatai/rag/api/question/QuestionController.java`
- `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagRequest.java`
- `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagResponse.java`
- `backend-java/src/main/java/com/huatai/rag/api/rag/dto/SourceDocumentDto.java`
- `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RecallDocumentDto.java`
- `backend-java/src/main/java/com/huatai/rag/api/upload/dto/ProcessedFilesResponse.java`
- `backend-java/src/main/java/com/huatai/rag/api/question/dto/TopQuestionsResponse.java`
- `backend-java/src/main/java/com/huatai/rag/api/common/ApiExceptionHandler.java`

### Application layer

- `backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java`
- `backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java`
- `backend-java/src/main/java/com/huatai/rag/application/registry/ProcessedFileQueryApplicationService.java`
- `backend-java/src/main/java/com/huatai/rag/application/history/QuestionHistoryApplicationService.java`
- `backend-java/src/main/java/com/huatai/rag/application/common/ContextAssemblyService.java`

### Domain layer

- `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentFileRecord.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionJobRecord.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionStatus.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/IndexNamingPolicy.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/ChunkedDocument.java`
- `backend-java/src/main/java/com/huatai/rag/domain/parser/DocumentParser.java`
- `backend-java/src/main/java/com/huatai/rag/domain/parser/ParserRequest.java`
- `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedDocument.java`
- `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedPage.java`
- `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedChunk.java`
- `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedAsset.java`
- `backend-java/src/main/java/com/huatai/rag/domain/retrieval/SearchMethod.java`
- `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievedDocument.java`
- `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalResult.java`
- `backend-java/src/main/java/com/huatai/rag/domain/retrieval/EmbeddingPort.java`
- `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalPort.java`
- `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RerankPort.java`
- `backend-java/src/main/java/com/huatai/rag/domain/rag/AnswerGenerationPort.java`
- `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentRegistryPort.java`
- `backend-java/src/main/java/com/huatai/rag/domain/history/QuestionHistoryPort.java`

### Infrastructure layer

- `backend-java/src/main/java/com/huatai/rag/infrastructure/config/AwsProperties.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/config/OpenSearchProperties.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/config/StorageProperties.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/config/RagProperties.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ClientConfig.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockEmbeddingAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockRerankAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/PromptTemplateFactory.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchIndexManager.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchDocumentWriter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchChunkMapper.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaClient.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaDocumentParserAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaResultMapper.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/DocumentFileEntity.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/IngestionJobEntity.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/QuestionHistoryEntity.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/DocumentFileJpaRepository.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/IngestionJobJpaRepository.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/QuestionHistoryJpaRepository.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/DocumentRegistryPersistenceAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/QuestionHistoryPersistenceAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/storage/LocalFileStorageAdapter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/support/RequestCorrelationFilter.java`
- `backend-java/src/main/java/com/huatai/rag/infrastructure/support/RetryUtils.java`
- `backend-java/src/main/resources/db/migration/V1__initial_schema.sql`
- `backend-java/src/main/resources/db/migration/V2__history_indexes.sql`
## Implementation Tasks

### Task 1: Create a clean worktree and freeze the migration baseline

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

- [ ] **Step 1: Start in a dedicated git worktree**

Run:

```bash
git worktree add ..\huatai_rag_java_migration -b codex/java-rag-migration
```

Expected: a clean worktree is created for migration work.

- [ ] **Step 2: Record the current API contract into fixtures**

Write request and response fixtures that preserve current Python field names, including `index_names`, `vec_docs_num`, `source_documents`, `recall_documents`, and `rerank_documents`.

- [ ] **Step 3: Build a baseline checklist**

Document exact behaviors to preserve:
- multi-index query joining
- vector/text/mix retrieval
- deterministic index naming
- processed file listing
- top question aggregation
- rerank threshold behavior
- response shape compatibility

- [ ] **Step 4: Capture representative regression data**

Prepare 3-5 representative PDFs and a `questions.csv` file with expected keywords and minimum source counts.

- [ ] **Step 5: Verify baseline assets exist**

Run: `Get-ChildItem backend-java\src\test\resources\fixtures -Recurse`
Expected: contract fixtures and regression assets are present.

- [ ] **Step 6: Commit the baseline package**

```bash
git add backend-java/src/test/resources docs/superpowers/plans/migration-baseline-checklist.md
git commit -m "test: add migration baseline fixtures"
```

### Task 2: Bootstrap the layered Spring Boot project

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

- [ ] **Step 2: Run the bootstrap test and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test`
Expected: FAIL before the Maven project exists.

- [ ] **Step 3: Create the Maven project and root Spring Boot class**

Dependencies must include:
- spring-boot-starter-web
- spring-boot-starter-validation
- spring-boot-starter-data-jpa
- spring-ai Bedrock starter
- flyway-core
- postgresql
- aws sdk v2 modules
- opensearch-java
- junit 5, mockito, testcontainers, wiremock

- [ ] **Step 4: Add profiles and set server port to `8001`**

This preserves frontend compatibility.

- [ ] **Step 5: Run the bootstrap test again**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test`
Expected: PASS.

- [ ] **Step 6: Commit the layered project skeleton**

```bash
git add backend-java/pom.xml backend-java/README.md backend-java/src/main backend-java/src/test/java/com/huatai/rag/RagApplicationTest.java
git commit -m "build: bootstrap layered spring boot rag backend"
```

### Task 3: Add infrastructure configuration and persistence adapters

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/AwsProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/OpenSearchProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/StorageProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/RagProperties.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ClientConfig.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/DocumentFileEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/IngestionJobEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/QuestionHistoryEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/DocumentFileJpaRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/IngestionJobJpaRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/QuestionHistoryJpaRepository.java`
- Create: `backend-java/src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `backend-java/src/main/resources/db/migration/V2__history_indexes.sql`
- Create: `backend-java/src/test/java/com/huatai/rag/integration/PostgresIntegrationTest.java`

- [ ] **Step 1: Write a failing Flyway/JPA integration test**

- [ ] **Step 2: Run it and verify failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PostgresIntegrationTest test`
Expected: FAIL.

- [ ] **Step 3: Implement property binding and infrastructure client beans**

Externalize AWS, OpenSearch, storage, threshold, timeout, and retry settings.

- [ ] **Step 4: Implement JPA entities, repositories, and migrations**

Tables must include `document_file`, `ingestion_job`, and `question_history`.

- [ ] **Step 5: Run the integration test again**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PostgresIntegrationTest test`
Expected: PASS.

- [ ] **Step 6: Commit configuration and persistence**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/config backend-java/src/main/java/com/huatai/rag/infrastructure/persistence backend-java/src/main/resources/db backend-java/src/test/java/com/huatai/rag/integration/PostgresIntegrationTest.java
git commit -m "feat: add infrastructure configuration and persistence adapters"
```

### Task 4: Implement the API layer with contract tests first

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/api/health/HealthController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/upload/UploadController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/question/QuestionController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagRequest.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagResponse.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/SourceDocumentDto.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RecallDocumentDto.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/upload/dto/ProcessedFilesResponse.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/question/dto/TopQuestionsResponse.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/common/ApiExceptionHandler.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/RagControllerContractTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/UploadControllerContractTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/QuestionControllerContractTest.java`

- [ ] **Step 1: Write failing MockMvc contract tests from baseline fixtures**

- [ ] **Step 2: Run the API tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest test`
Expected: FAIL.

- [ ] **Step 3: Implement DTOs and controllers with placeholder application-service dependencies**

Preserve all Python-compatible field names and endpoint paths.

- [ ] **Step 4: Re-run the API tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest test`
Expected: PASS.

- [ ] **Step 5: Commit the API layer**

```bash
git add backend-java/src/main/java/com/huatai/rag/api backend-java/src/test/java/com/huatai/rag/api
git commit -m "feat: add frontend-compatible api layer"
```
### Task 5: Implement domain models and business ports before adapters

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentFileRecord.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionJobRecord.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/IngestionStatus.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/IndexNamingPolicy.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/parser/DocumentParser.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/parser/ParserRequest.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedDocument.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedPage.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedChunk.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedAsset.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/SearchMethod.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievedDocument.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalResult.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/EmbeddingPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RetrievalPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/retrieval/RerankPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/AnswerGenerationPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/document/DocumentRegistryPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/history/QuestionHistoryPort.java`
- Create: `backend-java/src/test/java/com/huatai/rag/domain/DomainModelTest.java`

- [ ] **Step 1: Write failing domain tests for index naming and retrieval models**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=DomainModelTest test`
Expected: FAIL.

- [ ] **Step 3: Implement pure domain records, enums, and ports**

Do not depend on Spring, JPA, AWS SDK, or OpenSearch client classes.

- [ ] **Step 4: Re-run the domain tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=DomainModelTest test`
Expected: PASS.

- [ ] **Step 5: Commit the domain contracts**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain backend-java/src/test/java/com/huatai/rag/domain/DomainModelTest.java
git commit -m "feat: add domain models and business ports"
```

### Task 6: Implement OpenSearch infrastructure adapters

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchIndexManager.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchDocumentWriter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchChunkMapper.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/opensearch/IndexNamingPolicyTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/integration/OpenSearchIntegrationTest.java`

- [ ] **Step 1: Write failing tests for index naming, mapping creation, and mixed retrieval behavior**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest test`
Expected: FAIL.

- [ ] **Step 3: Implement deterministic index naming using the current Python compatibility rule**

Preserve `md5(filename)[:8]` unless the migration explicitly chooses otherwise.

- [ ] **Step 4: Implement index creation, chunk mapping, vector search, text search, mixed search, and bulk write support**

Keep explicit control over OpenSearch field names:
- `sentence_vector`
- `paragraph`
- `sentence`
- `metadata.*`

- [ ] **Step 5: Re-run the OpenSearch tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest test`
Expected: PASS.

- [ ] **Step 6: Commit the OpenSearch adapters**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch backend-java/src/test/java/com/huatai/rag/infrastructure/opensearch backend-java/src/test/java/com/huatai/rag/integration/OpenSearchIntegrationTest.java
git commit -m "feat: add opensearch infrastructure adapters"
```

### Task 7: Implement Bedrock infrastructure adapters

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockEmbeddingAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockRerankAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/PromptTemplateFactory.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/support/RetryUtils.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/PromptTemplateFactoryTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/BedrockAdapterWiringTest.java`

- [ ] **Step 1: Write failing tests for prompt construction and adapter wiring**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest test`
Expected: FAIL.

- [ ] **Step 3: Implement prompt construction consistent with the Python backend**

Preserve:
- expert-style system prompt
- direct-answer user prompt shape
- context-first assembly

- [ ] **Step 4: Implement embedding, rerank, and answer adapters with bounded retry behavior**

- [ ] **Step 5: Re-run the tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest test`
Expected: PASS.

- [ ] **Step 6: Commit the Bedrock adapters**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock backend-java/src/main/java/com/huatai/rag/infrastructure/support/RetryUtils.java backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock
git commit -m "feat: add bedrock infrastructure adapters"
```

### Task 8: Implement BDA parsing and parser normalization

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaClient.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaDocumentParserAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaResultMapper.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/bda/BdaResultMapperTest.java`
- Create: `backend-java/src/test/resources/fixtures/parser/bda-sample-response.json`

- [ ] **Step 1: Write failing tests for page extraction, section lineage, chunk mapping, and asset references**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=BdaResultMapperTest test`
Expected: FAIL.

- [ ] **Step 3: Implement the raw BDA client and response polling flow**

- [ ] **Step 4: Implement mapping from BDA results to domain `ParsedDocument`/`ParsedChunk`/`ParsedAsset`**

Preserve:
- page number
- section path
- paragraph text
- short sentence form for embedding/rerank
- asset references
- parser provenance

- [ ] **Step 5: Re-run the parser tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=BdaResultMapperTest test`
Expected: PASS.

- [ ] **Step 6: Commit the BDA parser adapter**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/bda backend-java/src/test/java/com/huatai/rag/infrastructure/bda backend-java/src/test/resources/fixtures/parser/bda-sample-response.json
git commit -m "feat: add bda parser infrastructure adapter"
```

### Task 9: Implement application services for ingestion, query, registry, and history

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/application/registry/ProcessedFileQueryApplicationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/application/history/QuestionHistoryApplicationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/application/common/ContextAssemblyService.java`
- Create: `backend-java/src/test/java/com/huatai/rag/application/RagQueryApplicationServiceTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java`

- [ ] **Step 1: Write failing application-service orchestration tests**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest test`
Expected: FAIL.

- [ ] **Step 3: Implement `RagQueryApplicationService`**

Flow:
- accept multi-index request
- invoke retrieval port
- assemble context
- invoke rerank and answer generation ports
- record question history
- return response-ready result

- [ ] **Step 4: Implement `DocumentIngestionApplicationService`**

Flow:
- save file through storage adapter
- register document and ingestion job
- derive deterministic index name
- parse with BDA parser port
- normalize chunks
- embed short text
- ensure index exists
- bulk write chunks
- mark ingestion complete

- [ ] **Step 5: Implement registry and history application services**

- [ ] **Step 6: Re-run the application tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest test`
Expected: PASS.

- [ ] **Step 7: Commit the application layer**

```bash
git add backend-java/src/main/java/com/huatai/rag/application backend-java/src/test/java/com/huatai/rag/application
git commit -m "feat: add application orchestration services"
```
### Task 10: Replace API placeholders with real application-service wiring

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/upload/UploadController.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/question/QuestionController.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/common/ApiExceptionHandler.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/ApiLayerIntegrationTest.java`

- [ ] **Step 1: Write failing API integration tests against real application services**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=ApiLayerIntegrationTest test`
Expected: FAIL.

- [ ] **Step 3: Wire controllers to application services and map application results back into frontend-compatible DTOs**

- [ ] **Step 4: Implement frontend-safe exception mapping for validation, BDA, Bedrock, and OpenSearch failures**

- [ ] **Step 5: Re-run API integration tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=ApiLayerIntegrationTest test`
Expected: PASS.

- [ ] **Step 6: Commit API wiring**

```bash
git add backend-java/src/main/java/com/huatai/rag/api backend-java/src/test/java/com/huatai/rag/api/ApiLayerIntegrationTest.java
git commit -m "feat: wire api layer to application services"
```

### Task 11: Add observability, local storage support, and cutover readiness guards

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/storage/LocalFileStorageAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/support/RequestCorrelationFilter.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/support/RequestCorrelationFilterTest.java`
- Modify: `backend-java/README.md`

- [ ] **Step 1: Write failing tests for request correlation and storage behavior**
- [ ] **Step 2: Run the tests and confirm failure**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RequestCorrelationFilterTest test`
Expected: FAIL.

- [ ] **Step 3: Implement local file storage and request correlation support**

- [ ] **Step 4: Re-run the support tests**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RequestCorrelationFilterTest test`
Expected: PASS.

- [ ] **Step 5: Update the backend README with local run, config, and verification instructions**

- [ ] **Step 6: Commit observability and storage support**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/storage backend-java/src/main/java/com/huatai/rag/infrastructure/support backend-java/src/test/java/com/huatai/rag/infrastructure/support backend-java/README.md
git commit -m "feat: add storage and observability support"
```

### Task 12: Run regression, end-to-end verification, and prepare cutover

**Files:**
- Create: `backend-java/src/test/java/com/huatai/rag/regression/RagRegressionTest.java`
- Create: `backend-java/src/test/java/com/huatai/rag/regression/IngestionRegressionTest.java`
- Create: `docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md`

- [ ] **Step 1: Write failing regression tests using the baseline PDFs and questions**

Cover:
- ingestion success on representative PDFs
- retrieval overlap expectations
- source document presence
- answer keyword expectations

- [ ] **Step 2: Run the regression tests and confirm failure before final parity fixes**

Run: `mvn -f backend-java/pom.xml -q -Dtest=RagRegressionTest,IngestionRegressionTest test`
Expected: FAIL.

- [ ] **Step 3: Close any remaining parity gaps revealed by the regression suite**

Fix only concrete mismatches in:
- retrieval behavior
- response mapping
- BDA normalization
- history recording

- [ ] **Step 4: Run the full test suite**

Run: `mvn -f backend-java/pom.xml test`
Expected: PASS.

- [ ] **Step 5: Smoke test the service locally**

Run:

```bash
mvn -f backend-java/pom.xml spring-boot:run
curl http://localhost:8001/health
```

Expected: service starts on port `8001` and `/health` returns success.

- [ ] **Step 6: Run the React frontend against the Java backend**

Verify:
- upload flow works
- processed file list renders
- Q&A works
- source documents display correctly
- no frontend API rewrite is required

- [ ] **Step 7: Write the cutover checklist**

Include:
- required environment variables
- DB migration applied
- OpenSearch connectivity verified
- Bedrock access verified
- BDA parsing verified
- frontend base URL switched
- rollback rule defined

- [ ] **Step 8: Commit regression coverage and cutover docs**

```bash
git add backend-java/src/test/java/com/huatai/rag/regression docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md
git commit -m "test: add layered migration regression and cutover checklist"
```

## Verification Command Set

```bash
mvn -f backend-java/pom.xml -q -Dtest=RagApplicationTest test
mvn -f backend-java/pom.xml -q -Dtest=PostgresIntegrationTest test
mvn -f backend-java/pom.xml -q -Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest test
mvn -f backend-java/pom.xml -q -Dtest=DomainModelTest test
mvn -f backend-java/pom.xml -q -Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest test
mvn -f backend-java/pom.xml -q -Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest test
mvn -f backend-java/pom.xml -q -Dtest=BdaResultMapperTest test
mvn -f backend-java/pom.xml -q -Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest test
mvn -f backend-java/pom.xml -q -Dtest=ApiLayerIntegrationTest test
mvn -f backend-java/pom.xml -q -Dtest=RequestCorrelationFilterTest test
mvn -f backend-java/pom.xml -q -Dtest=RagRegressionTest,IngestionRegressionTest test
mvn -f backend-java/pom.xml test
```

## Codex Execution Rules For The New Worktree

- Start from Task 1 and do not skip forward.
- Keep the existing Python backend under `api/` intact until Task 12 is complete.
- Do not change frontend endpoint names or response field names.
- Keep OpenSearch field names explicit and compatible.
- Keep parser contracts in `domain` and AWS/BDA implementation details in `infrastructure`.
- Use `application` only for orchestration, not for raw OpenSearch or AWS SDK calls.
- If the plan needs adjustment, make the smallest possible change and explain the reason before proceeding.
- After each task, report what changed, what tests ran, and whether the acceptance condition passed.

## Definition of Done

The migration is complete only when all of the following are true:

- `backend-java/` implements the backend using the layered `api/application/domain/infrastructure` structure.
- The Java backend exposes all current frontend-required endpoints.
- Upload, processed-file lookup, and RAG answer flows work end-to-end.
- OpenSearch vector, text, and mixed retrieval are preserved.
- Bedrock embedding, rerank, and answer generation are preserved.
- AWS BDA parsing is integrated behind the domain parser port.
- Question history endpoints are preserved.
- Regression tests pass on representative documents and questions.
- The React frontend runs against the Java backend without code-level API rewrites.
