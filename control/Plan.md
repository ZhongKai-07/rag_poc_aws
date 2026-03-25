# Spring Boot RAG Migration Plan

## Source Of Truth

Primary implementation plan:

- `docs/superpowers/plans/2026-03-19-springboot-rag-migration-layered-plan.md`

This file is the execution overlay for current status, milestone boundaries, verification, and decisions already made.

## Target Architecture

- `api`
  - HTTP endpoints, request validation, DTO mapping, exception handling
- `application`
  - use-case orchestration only
- `domain`
  - business models, value objects, enums, ports, invariants
- `infrastructure`
  - OpenSearch, Bedrock, BDA, persistence, storage, support wiring

## Milestones

### Completed Milestones

1. Baseline freeze
   - Status: completed
   - Acceptance:
     - contract fixtures exist
     - regression question baseline exists
     - migration baseline checklist exists
   - Verification:
     - `Get-ChildItem backend-java\src\test\resources\fixtures -Recurse`

2. Spring Boot bootstrap
   - Status: completed
   - Acceptance:
     - `backend-java/` Maven project exists
     - Spring context bootstrap test passes
     - server port configured to `8001`
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagApplicationTest" test`

3. Infrastructure config and persistence
   - Status: completed
   - Acceptance:
     - property binding classes exist
     - Flyway migrations create required tables
     - repositories persist and query records
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PostgresIntegrationTest" test`

4. API contract layer
   - Status: completed
   - Acceptance:
     - controllers and DTOs preserve endpoint and field compatibility
     - MockMvc contract tests pass
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest" test`

5. Domain contracts
   - Status: completed
   - Acceptance:
     - index naming and retrieval domain behavior tested
     - domain has no Spring/JPA/AWS/OpenSearch client dependency
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=DomainModelTest" test`

6. OpenSearch adapters
   - Status: completed
   - Acceptance:
     - compatible index naming preserved
     - explicit field mapping preserved
     - vector/text/mix retrieval adapter behavior covered
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest" test`

7. Bedrock adapters
   - Status: completed
   - Acceptance:
     - prompt factory shape preserved
     - adapter wiring and retry helper exist
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest" test`

### Remaining Milestones

8. BDA parsing and normalization
   - Status: completed
   - Acceptance:
     - BDA client polling flow exists
     - BDA result mapping preserves page, section path, paragraph, short sentence, assets, provenance
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=BdaResultMapperTest" test`

9. Application orchestration
   - Status: completed
   - Acceptance:
     - ingestion/query/history/registry flows implemented in `application/`
     - orchestration tests pass
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest" test`

10. Real API wiring
   - Status: completed
   - Acceptance:
     - controllers use real application services
     - placeholder wiring removed
     - API integration test passes
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=ApiLayerIntegrationTest" test`

11. Storage and observability
   - Status: completed
   - Acceptance:
      - local file storage adapter exists
      - request correlation support exists
      - README updated
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RequestCorrelationFilterTest" test`

12. Regression and cutover readiness
   - Status: completed
   - Acceptance:
      - regression tests pass
      - full suite passes
     - local smoke test passes when `8001` is available for the Java process
     - cutover checklist exists
   - Verification:
     - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagRegressionTest,IngestionRegressionTest" test`
     - `mvn -f backend-java/pom.xml test`
     - `mvn -f backend-java/pom.xml spring-boot:run`
     - `curl http://localhost:8001/health`

## Stop And Fix Rules

Stop and fix before moving on when any of the following happens:

- The current milestone test does not fail for the expected reason before implementation.
- A milestone verification command fails after implementation.
- A new change breaks a previously completed milestone verification.
- A change would alter frontend field names, endpoint names, or response shapes.
- A change would move AWS/OpenSearch/JPA concerns into `domain/`.
- A change would require deleting or breaking the Python backend.
- The milestone status in these root documents is no longer accurate.

## Documentation Update Rule

After every milestone, update:

- `Prompt.md` if scope, constraints, or done criteria changed
- `Plan.md` if milestone status or execution decisions changed
- `Implement.md` if working rules changed
- `Documentation.md` with current progress, decisions, and next step

## Decision Notes

- Current worktree is already isolated:
  - `C:\Users\zhong kai\.codex\worktrees\ff34\huatai_rag_github_share`
- Current HEAD is detached and work continues there intentionally.
- Java target is temporarily `17` because the local machine currently exposes only JDK 17.
- Test profile uses H2 in PostgreSQL compatibility mode, so local PostgreSQL is not required for current test milestones.
- When Maven needs to resolve newly added plugins or dependencies in this environment, use `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` because the default local repository setting points at an unavailable `D:\download\Maven\localRepository`.
- Task 10 removed the temporary API-DTO bridge from `application/`.
  - Controllers now perform DTO-to-application and application-to-DTO mapping explicitly.
  - Task 11 completed the local storage adapter, request correlation filter, and the main runtime bean graph needed for the real service stack in the test profile.
- Task 12 added regression tests for Python-compatible rerank fallback behavior and duplicate-upload skip behavior.
  - `RagRegressionTest` now locks the Python no-doc fallback when rerank results are empty.
  - `IngestionRegressionTest` now locks Python-compatible duplicate upload skipping for already completed files.
  - `application-test.yml` now excludes Spring AI Bedrock auto-config classes so the full Maven suite can run in this environment without creating extra Bedrock event loops.
  - On `2026-03-19`, exact smoke verification on `http://localhost:8001/health` was blocked because the Python baseline process (`python.exe` PID `42828`) was already listening on port `8001`.
  - On `2026-03-20`, exact smoke verification on `http://localhost:8001/health` was completed from a Java process launched with `backend-java/.env` after port `8001` was available.
  - On `2026-03-20`, live AWS verification showed that BDA success can resolve to `job_metadata.json`; the Java client now follows `output_metadata[].segment_metadata[].standard_output_path`, which restored real parsing for the representative PDF and allowed OpenSearch writes into index `ced4c5ef`.
  - The current live blocker is no longer exact-port smoke or OpenSearch `401`; it is Bedrock answer-generation access for the configured Anthropic model in the active region/account context.
- `backend-java/target/` must stay ignored and must not be committed.
- On `2026-03-21`, the Bedrock answer model blocker was resolved by switching from `anthropic.claude-3-5-sonnet-20240620-v1:0` to `qwen.qwen3-235b-a22b-2507-v1:0` (matching the Python baseline's working model). `BEDROCK_REGION` changed to `us-west-2`. `BedrockAnswerGenerationAdapter` updated to support both Anthropic and OpenAI-compatible payload formats.
- On `2026-03-21`, model IDs were externalized to `application.yml` via `RAG_ANSWER_MODEL_ID`, `RAG_EMBEDDING_MODEL_ID`, `RAG_RERANK_MODEL_ID` environment variables so future model changes only require `.env` edits.
- On `2026-03-21`, frontend upload "Upload Failed" was diagnosed as a missing CORS configuration. The Java backend had no CORS headers, so the browser blocked cross-origin requests from `localhost:8080` (Vite) to `localhost:8001` (backend) before they reached the server. `CorsConfig.java` was added mirroring the Python baseline's `allow_origins=["*"]` policy.
- On `2026-03-21`, frontend upload also required creating `frontend/.env` with `VITE_API_BASE_URL=http://localhost:8001`. Only `.env.example` existed; without `.env`, the Vite env variable was `undefined` and `fetch` targeted `undefined/upload_files`.
- On `2026-03-21`, RAG query against index `4c408463` failed with `Field 'sentence_vector' is not knn_vector type`. Root cause: the index was created (likely by Python baseline) without explicit KNN mapping, so OpenSearch auto-inferred `sentence_vector` as `float`. Java's `ensureIndex()` skips creation if the index already exists, so the bad mapping persisted. Fix: deleted index `4c408463` via OpenSearch REST API; re-upload will recreate with correct `knn_vector` mapping from `OpenSearchIndexManager.buildIndexMapping()`.
- On `2026-03-21`, added `backend-java/diagnose-aws.sh` diagnostic script covering: env vars, `/health`, OpenSearch cluster, S3 bucket, Bedrock model invoke, PostgreSQL TCP, and frontend `.env` checks.
- On `2026-03-21`, fixed `OpenSearchIndexManager.hasKnnVectorMapping()` returning `true` on error (`ResponseException` caught as `IOException`). Changed to return `false` so indices with bad mapping are deleted and recreated. Removed redundant `ensureIndex()` in `OpenSearchDocumentWriter.writeChunks()`. Added diagnostic logging to `ensureIndex()` and `createIndex()`.
- On `2026-03-21`, rewritten `ensureIndex()` to eliminate HEAD+GET two-step inconsistency. The OpenSearch REST client over HTTP/2 returned 200 for HEAD but 404 for GET /_mapping on the same index, causing DELETE to fail with 404. New approach: `checkMappingStatus()` uses GET /_mapping as single source of truth returning 3-state enum (`VALID`/`INVALID`/`NOT_FOUND`). `deleteIndexIfExists()` tolerates 404 on DELETE. All 42 tests pass.
- On `2026-03-22`, `BedrockAnswerGenerationAdapter` switched from `invokeModel` to `converse` API because third-party models (Qwen) only support `converse`. This also eliminated the Anthropic/OpenAI dual-format handling and the `ObjectMapper` dependency.
- On `2026-03-22`, answer model default changed to `qwen.qwen3-235b-a22b-2507-v1:0` and `BEDROCK_REGION` default changed to `us-west-2` in `application.yml` and `RagProperties.java`. Root cause: IDE startup configuration was not loading `.env` variables, so the backend fell back to stale hardcoded defaults.
- On `2026-03-22`, PostgreSQL and OpenSearch data fully cleared for a clean restart. All user indices gone; all three PG tables at 0 rows.
- On `2026-03-22`, model ID corrected from `qwen.qwen3-vl-235b-a22b` (invalid, extra `-vl` suffix, missing version) to `qwen.qwen3-235b-a22b-2507-v1:0` (matching Python baseline `api/llm_processor.py`). `.env` `BEDROCK_REGION` also corrected from `us-west-1` to `us-west-2`.
- On `2026-03-22`, confirmed Spring Boot does not auto-load `.env` files — env vars must be set via IDE launch config or shell. The `.env` file is a reference template only.
- On `2026-03-22`, **end-to-end RAG pipeline verified**: upload → BDA parsing → OpenSearch indexing → retrieval → Bedrock `converse` answer generation all working. Migration is functionally complete.
