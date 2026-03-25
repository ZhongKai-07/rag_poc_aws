# Migration Documentation

## Current Status

Current milestone status:

- Task 1: completed
- Task 2: completed
- Task 3: completed
- Task 4: completed
- Task 5: completed
- Task 6: completed
- Task 7: completed
- Task 8: completed
- Task 9: completed
- Task 10: completed
- Task 11: completed
- Task 12: completed

The Java backend has all 12 migration tasks completed plus post-migration fixes for model access and CORS.

Latest runtime verification on `2026-03-20`:

- Java local profile starts successfully on port `8001` when launched with environment values loaded from `backend-java/.env`.
- `GET /health` returns `{"status":"healthy"}` from the Java process running on port `8001`.
- Upload flows now have the required Spring property bindings for S3 storage configuration and `backend-java/.env` includes `S3_DOCUMENT_BUCKET=bda-rag-docs-637992521376`.
- The Java BDA client now needs its own region instead of reusing `BEDROCK_REGION`; otherwise `us-east-1` BDA ARNs fail when the Bedrock runtime region is set elsewhere.
- Root cause for the `0 chunks / 0 embeddings` upload symptom is now identified and fixed in Java:
  - BDA success status can resolve to `job_metadata.json`, not directly to `standard_output/.../result.json`.
  - `job_metadata.json` is valid JSON, so the old client returned it as the parsed payload and the mapper correctly produced `0` chunks from that metadata-only shape.
  - `BdaClient` now follows `output_metadata[].segment_metadata[].standard_output_path` to fetch the real `result.json`.
- Live upload of the representative PDF bytes under the new logical filename `diag-otcd-live-20260320-fix.pdf` now returns `Files processed successfully` and the Java logs show:
  - `Parsed document diag-otcd-live-20260320-fix.pdf into 29 chunks`
  - `Generated 29 embeddings for diag-otcd-live-20260320-fix.pdf`
  - `Writing 29 chunks into OpenSearch index ced4c5ef`
- OpenSearch authentication is no longer the active blocker in this workspace:
  - Direct authenticated REST checks against `ced4c5ef` succeed with the current `.env` Basic Auth credentials.
  - `_cat/indices/ced4c5ef?format=json` reports `docs.count=29`.
  - `ced4c5ef/_search` returns hits for the indexed document text.
- `/rag_answer` against the real Java-written index `ced4c5ef` now progresses past OpenSearch retrieval and fails later in Bedrock answer generation with `BedrockRuntime 400 ValidationException` because the current Anthropic model access is not allowed from the active country/region context.

Latest updates on `2026-03-21`:

- Bedrock answer model switched from `anthropic.claude-3-5-sonnet-20240620-v1:0` to `qwen.qwen3-235b-a22b-2507-v1:0` to match the Python baseline's working configuration.
- `BEDROCK_REGION` changed from `ap-northeast-1` to `us-west-2` to align with the Python baseline.
- `BedrockAnswerGenerationAdapter` updated to support both Anthropic Messages API and OpenAI-compatible (Qwen) request/response formats. Model format is auto-detected based on the `answerModelId` prefix.
- Model IDs externalized in `application.yml` via `RAG_ANSWER_MODEL_ID`, `RAG_EMBEDDING_MODEL_ID`, `RAG_RERANK_MODEL_ID` environment variables.
- Frontend upload "Upload Failed" diagnosed: root cause was missing CORS configuration in the Java backend. Browser blocked cross-origin requests from Vite dev server (`localhost:8080`) to Java backend (`localhost:8001`) before they reached the server, producing no server-side logs.
- Added `CorsConfig.java` with `WebMvcConfigurer` CORS mapping matching the Python baseline's `CORSMiddleware(allow_origins=["*"])` policy.
- All 42 tests pass after these changes.
- Frontend upload also required `frontend/.env` with `VITE_API_BASE_URL=http://localhost:8001`; without it, `import.meta.env.VITE_API_BASE_URL` was `undefined` and `fetch` targeted `undefined/upload_files`.
- RAG query against legacy index `4c408463` failed with `Field 'sentence_vector' is not knn_vector type`. The index was created without explicit KNN mapping, so `sentence_vector` was auto-inferred as `float`. Deleted the index via `curl -X DELETE`; re-upload will recreate with correct `knn_vector` mapping from `OpenSearchIndexManager.buildIndexMapping()`.
- Added `backend-java/diagnose-aws.sh` diagnostic script for verifying AWS service connectivity (OpenSearch, S3, Bedrock, PostgreSQL, frontend config).
- `OpenSearchIndexManager.ensureIndex()` had a bug: `hasKnnVectorMapping()` caught `ResponseException` (404) as `IOException` and returned `true` (assuming mapping was valid), causing bad indices to persist. Fixed to return `false` on any error so the index gets deleted and recreated.
- Removed redundant `ensureIndex()` call in `OpenSearchDocumentWriter.writeChunks()` — `DocumentChunkWriter.ensureIndexExists()` already handles it.
- Added logging to `ensureIndex()` and `createIndex()` for diagnosing index creation issues.
- Deleted index `9de75ce0` (74 docs, `sentence_vector: float`) created before the fix — requires re-upload.
- Rewritten `ensureIndex()` to eliminate HEAD+GET two-step inconsistency. The OpenSearch REST client over HTTP/2 returned 200 for HEAD `/4f829d9d` but 404 for GET `/4f829d9d/_mapping`, causing a cascading failure when DELETE also returned 404. New approach uses `checkMappingStatus()` with GET /_mapping as single source of truth (returns `VALID`/`INVALID`/`NOT_FOUND` enum). `deleteIndexIfExists()` tolerates 404. All 42 tests pass.

Latest updates on `2026-03-22`:

- `BedrockAnswerGenerationAdapter` switched from `invokeModel` API to `converse` API. Third-party models (Qwen, etc.) only support `converse`; `invokeModel` returns "The provided model identifier is invalid". This also removed the Anthropic/OpenAI dual-format request/response handling and the `ObjectMapper` dependency from the adapter.
- Answer model default changed from `anthropic.claude-3-5-sonnet-20240620-v1:0` to `qwen.qwen3-235b-a22b-2507-v1:0` in both `application.yml` and `RagProperties.java`. This ensures the correct model is used even when `RAG_ANSWER_MODEL_ID` env var is not loaded by the IDE.
- `BEDROCK_REGION` default changed from `${AWS_DEFAULT_REGION:us-east-1}` to `us-west-2` in `application.yml` for the same reason.
- Diagnosed that the IDE startup configuration was not loading all `.env` variables — `RAG_ANSWER_MODEL_ID`, `S3_DOCUMENT_BUCKET`, `BEDROCK_REGION` were missing from the runtime env, causing Java to use hardcoded defaults.
- Cleared all PostgreSQL data (`document_file`, `ingestion_job`, `question_history` — all 0 rows) and confirmed all user OpenSearch indices are gone (only system `.plugins-*` indices remain). Clean slate for re-upload.
- **Model ID corrected**: `qwen.qwen3-235b-a22b-2507-v1:0` was an invalid model identifier (the `-vl` suffix and missing version number). Changed to `qwen.qwen3-235b-a22b-2507-v1:0` in `.env`, `application.yml`, and `RagProperties.java` to match the Python baseline (`api/llm_processor.py`). This was the root cause of the persistent `ValidationException: The provided model identifier is invalid`.
- **`.env` BEDROCK_REGION corrected**: was `us-west-1` (typo), changed to `us-west-2`.
- Backend recompiled and restarted successfully. Health check returns `{"status":"healthy"}`. 42 tests pass.
- **End-to-end RAG pipeline verified on `2026-03-22`**: upload → BDA parsing → OpenSearch indexing → vector/text retrieval → Bedrock `converse` API answer generation all working. The full chain is operational with `qwen.qwen3-235b-a22b-2507-v1:0` in `us-west-2`.
- Root cause confirmed: Spring Boot does not auto-load `.env` files. The IDE launch command must explicitly set all required env vars (especially `BEDROCK_REGION=us-west-2`, `RAG_ANSWER_MODEL_ID`, `S3_DOCUMENT_BUCKET`). The `.env` file serves only as a reference/template.

## Completed Commits

- `dd907a21` `test: add migration baseline fixtures`
- `b1f9021c` `build: bootstrap layered spring boot rag backend`
- `34ea224d` `feat: add infrastructure configuration and persistence adapters`
- `c9e9f40f` `feat: add frontend-compatible api layer`
- `592f9c31` `feat: add domain models and business ports`
- `2210e272` `feat: add opensearch infrastructure adapters`
- `0908fd61` `feat: add bedrock infrastructure adapters`
- `baba1107` `docs: add migration control documents`

## Decisions And Reasons

1. Keep Python backend intact
   - Reason: it is the baseline, control group, and parity reference until the migration is complete.

2. Freeze frontend contract early
   - Reason: prevents later Java implementation work from drifting away from the existing React integration.

3. Introduce placeholder `application` interfaces in Task 4
   - Reason: allows API contract tests to pass before real orchestration is implemented in Task 9 and wired in Task 10.

4. Use H2 PostgreSQL compatibility mode in test profile
   - Reason: removes local PostgreSQL as a blocker for current milestone testing.

5. Temporarily target Java 17
   - Reason: local machine currently has JDK 17 available and the user explicitly approved the temporary downgrade to keep work moving.

6. Ignore `backend-java/target/`
   - Reason: Maven build output accidentally entered the index once and should never be committed.

7. Use the user `.m2` repository override when resolving new Maven artifacts locally
   - Reason: the default Maven local repository setting in this environment points to an unavailable `D:\download\Maven\localRepository`, while `C:\Users\zhong kai\.m2\repository` works.

8. Move DTO mapping back into controllers in Task 10
   - Reason: this restores the intended boundary where `application/` exposes native request/result models and `api/` owns HTTP contract mapping.

9. Complete the main runtime bean graph in Task 11
   - Reason: once controllers and application services were real, the test-profile startup path still needed concrete beans for storage, persistence adapters, parser wiring, and OpenSearch chunk writing.

10. Lock Python-compatible Task 12 regressions in tests
   - Reason: rerank-empty fallback behavior and duplicate-upload skip behavior were still implicit in the Python baseline and needed executable guards before cutover.

11. Exclude Spring AI Bedrock auto-config from the `test` profile only
   - Reason: the full Maven suite was instantiating extra Bedrock event loops unrelated to the layered adapters, which blocked local test execution on `2026-03-19`.

12. Load runtime environment from `backend-java/.env`
   - Reason: the live Java startup and AWS connectivity checks on `2026-03-20` showed that `backend-java/src/main/resources/aws_config.txt` was not the active runtime source for the current workflow.

13. Bind S3 storage environment variables in `application.yml`
   - Reason: the upload path was failing immediately with `documentBucket must not be blank` because `S3_DOCUMENT_BUCKET`, `S3_DOCUMENT_PREFIX`, and `S3_BDA_OUTPUT_PREFIX` were documented but not actually wired into Spring configuration.

14. Add a dedicated BDA runtime region setting
   - Reason: live upload verification on `2026-03-20` showed that BDA ARNs in `us-east-1` fail when the Java BDA client reuses `BEDROCK_REGION=ap-northeast-1`.

15. Follow `standard_output_path` when BDA returns job metadata
   - Reason: live AWS verification on `2026-03-20` showed that `GetDataAutomationStatus` can lead the Java client to `job_metadata.json`, whose `output_metadata[].segment_metadata[].standard_output_path` must be followed to reach the real parsed `result.json`.

16. Treat Bedrock model access as the current end-to-end blocker, not OpenSearch auth
   - Reason: after the BDA client fix on `2026-03-20`, live upload wrote 29 chunks into OpenSearch index `ced4c5ef`, direct OpenSearch search returned hits, and `/rag_answer` advanced to a BedrockRuntime `400` model-access validation failure instead of failing at OpenSearch.

17. Switch answer model from Anthropic Claude to Qwen
   - Reason: the Python baseline had already switched to `qwen.qwen3-235b-a22b-2507-v1:0` in `us-west-2`; the Java backend was still using `anthropic.claude-3-5-sonnet-20240620-v1:0` in `ap-northeast-1`, which was rejected with a `400 ValidationException` due to region/country access restrictions.

18. Support multi-provider Bedrock payload formats in `BedrockAnswerGenerationAdapter`
   - Reason: Qwen uses OpenAI-compatible format (`messages` array with system role, `choices[].message.content` response), while Anthropic uses its own format (`anthropic_version`, separate `system` field, `content[].text` response). The adapter must handle both to allow model switching via configuration.

19. Externalize model IDs to environment variables
   - Reason: model IDs were hardcoded in `RagProperties.java` defaults. Adding `RAG_ANSWER_MODEL_ID`, `RAG_EMBEDDING_MODEL_ID`, `RAG_RERANK_MODEL_ID` bindings in `application.yml` allows switching models without code changes.

20. Add CORS configuration to Java backend
   - Reason: the frontend Vite dev server runs on `localhost:8080` while the Java backend runs on `localhost:8001`. Without CORS headers, the browser blocks cross-origin requests entirely (including the preflight OPTIONS), so no request reaches the server and no server-side logs are produced. The Python baseline had `CORSMiddleware(allow_origins=["*"])` configured; the Java backend was missing the equivalent.

21. Create `frontend/.env` for local development
   - Reason: only `.env.example` existed. Without `.env`, `import.meta.env.VITE_API_BASE_URL` resolved to `undefined`, causing all API requests to fail silently.

22. Delete and rebuild legacy OpenSearch indices with incorrect mapping
   - Reason: index `4c408463` had `sentence_vector` mapped as `float` (auto-inferred) instead of `knn_vector`. OpenSearch does not allow changing field types on existing indices. Java's `ensureIndex()` skips creation when the index exists, so the bad mapping persisted. Deleting and re-uploading is the only fix.

23. Fix `hasKnnVectorMapping()` error handling to return `false` instead of `true`
   - Reason: `ResponseException` extends `IOException`. When `hasKnnVectorMapping()` encountered a 404 or other error from `GET /_mapping`, it was caught by `catch (IOException)` and returned `true` (assuming valid). This caused `ensureIndex()` to skip recreating indices with bad mapping. Changed to return `false` so the index gets deleted and recreated.

24. Remove redundant `ensureIndex()` call from `OpenSearchDocumentWriter.writeChunks()`
   - Reason: `DocumentChunkWriter.ensureIndexExists()` (called from the ingestion service) already calls `ensureIndex()`. The redundant call in `writeChunks()` could race with the `_bulk` write and cause confusing error sequences.

25. Rewrite `ensureIndex()` to use GET /_mapping as single source of truth
   - Reason: the HEAD+GET two-step approach was unreliable over HTTP/2. HEAD returned 200 for index `4f829d9d` while GET /_mapping returned 404 for the same index in the same request, causing DELETE to fail with 404 and crash the upload flow. The new `checkMappingStatus()` method uses a single GET /_mapping call to determine both existence and validity, and `deleteIndexIfExists()` tolerates 404 on DELETE.

26. Switch `BedrockAnswerGenerationAdapter` from `invokeModel` to `converse` API
   - Reason: third-party models (Qwen, etc.) on Bedrock only support the `converse` API. `invokeModel` returns `ValidationException: The provided model identifier is invalid` for these models. The `converse` API uses a unified request/response format for all models, eliminating the Anthropic/OpenAI dual-format handling.

27. Change default answer model and region in `application.yml` and `RagProperties.java`
   - Reason: the IDE startup configuration was not loading `.env` variables (`RAG_ANSWER_MODEL_ID`, `BEDROCK_REGION`), causing Java to fall back to hardcoded defaults (`anthropic.claude-3-5-sonnet-20240620-v1:0`, `us-east-1`). Changed defaults to `qwen.qwen3-235b-a22b-2507-v1:0` and `us-west-2` so the backend works correctly even without env var overrides.

28. Clear PostgreSQL and OpenSearch data for clean restart
   - Reason: accumulated stale data from multiple debug iterations (wrong index mappings, failed uploads, mixed model configurations) was causing confusion. Clean slate allows a fresh end-to-end verification.

## Run And Demo Commands

Current milestone verification commands:

- Bootstrap:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagApplicationTest" test`
- Persistence:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PostgresIntegrationTest" test`
- API contract:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest" test`
- Domain:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=DomainModelTest" test`
- OpenSearch:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=IndexNamingPolicyTest,OpenSearchIntegrationTest" test`
- Bedrock:
  - `mvn -f backend-java/pom.xml -q "-Dspring.profiles.active=test" "-Dtest=PromptTemplateFactoryTest,BedrockAdapterWiringTest" test`
- BDA parsing:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=BdaResultMapperTest" test`
- BDA metadata indirection regression:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=BdaResultMapperTest#bdaClientFollowsStandardOutputPathWhenStatusOutputResolvesToJobMetadata" test`
- Application orchestration:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest" test`
- Application-safe regression for completed milestones touched by Task 9:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest,BdaResultMapperTest" test`
- API integration:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=ApiLayerIntegrationTest,RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest,RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest,BdaResultMapperTest" test`
- Storage and observability:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RequestCorrelationFilterTest,ApiLayerIntegrationTest,RagControllerContractTest,UploadControllerContractTest,QuestionControllerContractTest,RagQueryApplicationServiceTest,DocumentIngestionApplicationServiceTest,BdaResultMapperTest" test`
- Regression and cutover readiness:
  - `mvn -f backend-java/pom.xml -q "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" "-Dspring.profiles.active=test" "-Dtest=RagRegressionTest,IngestionRegressionTest" test`
  - `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test`

Future local service run command after wiring milestones are complete:

- `mvn -f backend-java/pom.xml spring-boot:run`
- `curl http://localhost:8001/health`

## Known Gaps

- Representative external baseline PDFs are still intentionally not committed under `backend-java/src/test/resources/fixtures/regression/documents/`, so binary-level ingestion parity beyond the filename/question fixtures still depends on operator-supplied files during cutover rehearsal.

## Known Warnings

- Spring AI Bedrock auto-configuration emits an AWS region warning in some tests.
- This warning has not blocked current milestone tests, but runtime configuration still needs proper AWS environment setup for real execution.
- In this shell environment, Maven may need `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` when new plugins or dependencies must be resolved.
- A `curl http://localhost:8001/health` response was observed on `2026-03-19`, but it came from the already-running Python baseline listener, not from a freshly started Java smoke process.

## Next Recommended Step

End-to-end RAG pipeline verified on `2026-03-22`. Next steps:

- Rehearse full frontend cutover using the existing checklist (`docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md`)
- Test with multiple file types and edge cases (large PDFs, special characters in filenames)
- Verify question history persistence and `/top_questions` endpoints with real data
- Consider production deployment readiness (logging, monitoring, error handling review)

## Maintenance Rule

These four root documents are part of the active development workflow and must be updated continuously as implementation progresses:

- `Prompt.md`
- `Plan.md`
- `Implement.md`
- `Documentation.md`
