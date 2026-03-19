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

Active next step:

- Exact Java smoke on port `8001` still requires a clean handoff window because the Python baseline process was already listening on that port during Task 12 verification on `2026-03-19`.
