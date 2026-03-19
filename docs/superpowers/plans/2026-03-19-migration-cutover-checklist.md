# Spring Boot RAG Migration Cutover Checklist

## Scope

Use this checklist when switching the React frontend and operational traffic from the Python backend under `api/` to the Spring Boot backend under `backend-java/`.

## Preconditions

- [ ] Confirm the migration branch includes Task 12 regression coverage:
  - `backend-java/src/test/java/com/huatai/rag/regression/RagRegressionTest.java`
  - `backend-java/src/test/java/com/huatai/rag/regression/IngestionRegressionTest.java`
- [ ] Confirm the full Java test suite is green:
  - `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test`
- [ ] Confirm the Python baseline under `api/` remains intact for rollback.
- [ ] Confirm `backend-java/target/` is not staged for commit or deployment packaging review.

## Required Environment Variables

- [ ] `AWS_DEFAULT_REGION`
- [ ] `BEDROCK_REGION` if it differs from `AWS_DEFAULT_REGION`
- [ ] `AWS_ACCESS_KEY_ID`
- [ ] `AWS_SECRET_ACCESS_KEY`
- [ ] `OPENSEARCH_ENDPOINT`
- [ ] `OPENSEARCH_USERNAME`
- [ ] `OPENSEARCH_PASSWORD`
- [ ] `BDA_PROJECT_ARN`
- [ ] `BDA_PROFILE_ARN`
- [ ] `BDA_STAGE`
- [ ] `DOCUMENT_ROOT`
- [ ] Any non-default RAG thresholds required by the target environment:
  - `RAG_VEC_SCORE_THRESHOLD`
  - `RAG_TEXT_SCORE_THRESHOLD`
  - `RAG_RERANK_SCORE_THRESHOLD`

## Infrastructure Readiness

- [ ] Run Flyway migrations in the target database and confirm schema version is current.
- [ ] Verify PostgreSQL connectivity from the Java runtime environment.
- [ ] Verify OpenSearch connectivity and credentials with the target cluster.
- [ ] Verify Bedrock runtime access for:
  - embeddings
  - rerank
  - answer generation
- [ ] Verify BDA project/profile access and parsing completion on at least one representative PDF.
- [ ] Confirm local or shared storage path exists and is writable for uploaded documents.

## Application Verification

- [ ] Stop any process already bound to port `8001` before Java smoke validation.
  - On `2026-03-19`, `python.exe` PID `42828` was already listening on `8001` in the shared workspace, so the Java smoke check could not claim that port without interrupting the baseline service.
- [ ] Start the Java backend on the cutover target host:
  - `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run`
- [ ] Verify health:
  - `curl http://localhost:8001/health`
- [ ] Verify upload flow using a representative PDF through `POST /upload_files`.
- [ ] Verify processed file lookup through:
  - `GET /processed_files`
  - `GET /get_index/{filename}`
- [ ] Verify Q&A flow through `POST /rag_answer` for:
  - single-index query
  - multi-index query
  - rerank-empty fallback response
- [ ] Verify question history through:
  - `GET /top_questions/{index_name}`
  - `GET /top_questions_multi`

## Frontend Switch

- [ ] Point the React frontend base URL to the Spring Boot backend.
- [ ] Verify no frontend code changes are required for:
  - upload
  - processed files
  - Q&A
  - source document rendering
  - top questions
- [ ] Keep the Python backend reachable until Java smoke, frontend checks, and operator sign-off are complete.

## Rollback Rule

- [ ] If health, upload, retrieval, answer generation, or question history regress after cutover:
  - switch frontend traffic back to the Python backend immediately
  - keep newly collected logs and request IDs for triage
  - do not continue cutover until the failing Java behavior is reproduced by the regression suite or a new failing test

## Sign-Off

- [ ] Engineering sign-off recorded.
- [ ] Operator or release owner sign-off recorded.
- [ ] Rollback contact and communication channel confirmed.
