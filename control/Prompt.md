# Spring Boot RAG Migration Prompt

## Goal

Build a Java backend under `backend-java/` that replaces the Python RAG backend with equivalent capabilities while preserving the existing frontend contract.

## Non-Goals

- Do not delete or break the existing Python backend under `api/`.
- Do not modify the React frontend to adapt to new backend shapes.
- Do not redesign API names, field names, or response structures.
- Do not reduce retrieval, rerank, parsing, or history capabilities.
- Do not expand scope into unrelated refactors outside the active milestone.

## Hard Constraints

- Use layered architecture under `backend-java/`:
  - `api`
  - `application`
  - `domain`
  - `infrastructure`
- Keep frontend-compatible endpoints:
  - `POST /upload_files`
  - `POST /rag_answer`
  - `GET /processed_files`
  - `GET /get_index/{filename}`
  - `GET /top_questions/{index_name}`
  - `GET /top_questions_multi`
  - `GET /health`
- Continue using:
  - AWS OpenSearch
  - AWS Bedrock
  - AWS BDA
- Preserve Python-compatible index naming:
  - `md5(filename)[:8]`
- Preserve OpenSearch field names:
  - `sentence_vector`
  - `paragraph`
  - `sentence`
  - `metadata.*`
- Keep all new work under `backend-java/` unless a small supporting change elsewhere is strictly required.
- Follow TDD:
  - write failing test first
  - verify failure
  - implement minimal code
  - verify pass
- During all future development, continuously update:
  - `Prompt.md`
  - `Plan.md`
  - `Implement.md`
  - `Documentation.md`
- Current temporary runtime constraint:
  - Java target is temporarily `17`, not `21`, by explicit user approval, to match the available local JDK.

## Deliverables

- A Spring Boot backend in `backend-java/` with layered structure.
- API compatibility with the current frontend.
- Infrastructure adapters for OpenSearch, Bedrock, BDA, storage, persistence, and support concerns.
- Domain models and ports that are independent of Spring/JPA/AWS/OpenSearch client APIs.
- Application services that orchestrate ingestion, retrieval, answer generation, processed file queries, and question history.
- Regression fixtures and regression tests proving parity against baseline behavior.
- Cutover checklist and local run documentation.

## Done When

The migration is complete only when all of the following are true:

- `backend-java/` exposes all required frontend endpoints.
- Upload, processed-file lookup, question history, and RAG answer flows work end-to-end.
- OpenSearch vector, text, and mix retrieval behavior is preserved.
- Bedrock embedding, rerank, and answer generation are preserved.
- AWS BDA parsing is integrated behind the domain parser port.
- React frontend can run against the Java backend without code-level API rewrites.
- Regression suite passes on representative documents and questions.
- Full test suite passes.
- Local smoke test passes on port `8001`.

## Current Completion Snapshot

Completed:

- Task 1: baseline fixtures and checklist
- Task 2: Spring Boot bootstrap
- Task 3: infrastructure config and persistence
- Task 4: frontend-compatible API contract layer
- Task 5: domain models and ports
- Task 6: OpenSearch adapters
- Task 7: Bedrock adapters
- Task 8: BDA parsing and normalization
- Task 9: application orchestration
- Task 10: real API wiring
- Task 11: storage and observability
- Task 12: regression coverage and cutover checklist

Post-migration fixes (2026-03-21 to 2026-03-22):

- `BedrockAnswerGenerationAdapter` switched from `invokeModel` API to `converse` API. Third-party models (Qwen, etc.) only support `converse`; `invokeModel` returns "model identifier is invalid". This also eliminated the Anthropic/OpenAI dual-format handling.
- Answer model default changed to `qwen.qwen3-235b-a22b-2507-v1:0` (user-selected from Bedrock model catalog).
- `BEDROCK_REGION` default changed to `us-west-2` in both `application.yml` and `RagProperties.java`.
- Model IDs externalized to environment variables (`RAG_ANSWER_MODEL_ID`, `RAG_EMBEDDING_MODEL_ID`, `RAG_RERANK_MODEL_ID`).
- CORS configuration added (`CorsConfig.java`) to allow cross-origin requests from the Vite dev server.
- Frontend `.env` created with `VITE_API_BASE_URL=http://localhost:8001`.
- `OpenSearchIndexManager.ensureIndex()` rewritten to use `GET /_mapping` as single source of truth (eliminated HEAD+GET two-step inconsistency over HTTP/2). Now uses 3-state `MappingStatus` enum (`VALID`/`INVALID`/`NOT_FOUND`). DELETE tolerates 404 gracefully.
- All legacy OpenSearch indices and PostgreSQL records cleared on `2026-03-22` for a clean restart.
- AWS connectivity diagnostic script added (`backend-java/diagnose-aws.sh`).

End-to-end verified on `2026-03-22`:

- Full RAG pipeline operational: upload → BDA parsing → OpenSearch indexing → retrieval → Bedrock `converse` answer generation.
- Model ID corrected to `qwen.qwen3-235b-a22b-2507-v1:0` (matching Python baseline).
- Spring Boot does not auto-load `.env`; IDE launch command must set env vars explicitly.

Active next step:

- Rehearse frontend cutover using the migration checklist.
- Test edge cases (multiple files, large PDFs, question history endpoints).
