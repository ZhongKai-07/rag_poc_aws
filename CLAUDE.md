# CLAUDE.md

This file provides repository-wide guidance for Claude Code and other coding agents working in this workspace.

## Current Project State

This repository is in a controlled migration from the legacy Python backend under `api/` to the Spring Boot backend under `backend-java/`.

- Frontend contract must stay unchanged.
- The Python backend remains the baseline and rollback target.
- The Java backend is the migration target and now covers the required layered architecture and frontend-facing endpoints.
- Current migration status:
  - Tasks 1-12 are complete. Post-migration runtime fixes applied on `2026-03-21`.
  - Java backend runs on port `8001`, `/health` verified, S3/BDA/OpenSearch/Bedrock all connected.
  - Answer model: `qwen.qwen3-235b-a22b-2507-v1:0` in `us-west-2` (matching Python baseline).
  - Frontend requires `frontend/.env` with `VITE_API_BASE_URL=http://localhost:8001`.
  - PostgreSQL and OpenSearch cleared on `2026-03-22`. Clean state for re-upload.
  - End-to-end RAG pipeline verified on `2026-03-22`: upload → parsing → indexing → retrieval → answer generation all working.
  - Code stability audit completed `2026-03-22`: all 4 critical issues (C1–C4) fixed, 42 tests pass. See `docs/ccodeReview/code-review-2026-03-22.md`.
  - Next steps: (a) BDA observability implementation; (b) rehearse frontend cutover.
  - **Active work**: docling-java integration — adding docling as alternative document parser alongside BDA. See `control/docling/` for control documents.
- Do not treat this repository as "Python-only" anymore.
- Do not treat the Java backend as fully cut over yet.

## Mandatory Source Of Truth

Read these first before making migration-related changes:

- `control/Prompt.md`
- `control/Plan.md`
- `control/Implement.md`
- `control/Documentation.md`
- `docs/superpowers/plans/2026-03-19-springboot-rag-migration-layered-plan.md`
- `docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md`
- `docs/superpowers/plans/2026-03-23-bda-observability.md`
- `backend-java/README.md`

Use them as the execution truth for scope, status, verification, and cutover readiness.

For docling-java integration work, read these first:

- `control/docling/Prompt.md`
- `control/docling/Plan.md`
- `control/docling/Implement.md`
- `control/docling/Documentation.md`

Interpretation rules:

- `control/Plan.md` plus the referenced migration plan are the execution source of truth.
- `control/Prompt.md` defines the migration goal and hard constraints.
- `control/Implement.md` defines milestone workflow and verification discipline.
- `control/Documentation.md` records decisions, progress, and the next recommended step.
- The cutover checklist governs frontend/traffic switching from `api/` to `backend-java/`.
- `docs/ccodeReview/code-review-2026-03-22.md` is the post-E2E code stability audit. All critical issues C1–C4 are resolved; remaining items are non-blocking.

## Repository Layout

- `frontend/`
  - React + TypeScript + Vite frontend.
  - Keep the frontend contract unchanged unless the user explicitly asks otherwise.
- `api/`
  - Legacy FastAPI baseline.
  - Primary role: behavioral reference, rollback path, and parity oracle.
  - Important runtime traits:
    - serves the frontend-facing API on port `8001`
    - persists processed file mappings in `processed_files.txt`
    - stores question history in `question_history/`
    - uses Python-compatible index naming `md5(filename)[:8]`
- `backend-java/`
  - Spring Boot migration target.
  - Required layered boundaries:
    - `api`
    - `application`
    - `domain`
    - `infrastructure`
  - The Java backend must preserve the existing frontend contract and Python-compatible retrieval/index behavior.

## Backend Responsibilities During Migration

### `api/` baseline rules

- Preserve the Python backend unless the user explicitly requests a Python-side change.
- Prefer reading `api/` to understand baseline behavior, payloads, naming, and fallback semantics.
- Do not casually refactor `api/` during Java migration work.
- If Java behavior is unclear, verify against `api/` before redesigning anything.

### `backend-java/` active development rules

- Default location for migration implementation work is `backend-java/`.
- Preserve these frontend-facing endpoints:
  - `POST /upload_files`
  - `POST /rag_answer`
  - `GET /processed_files`
  - `GET /get_index/{filename}`
  - `GET /top_questions/{index_name}`
  - `GET /top_questions_multi`
  - `GET /health`
- Preserve Python-compatible index naming and OpenSearch field names:
  - index name: `md5(filename)[:8]`
  - fields: `sentence_vector`, `paragraph`, `sentence`, `metadata.*`
- Keep AWS/OpenSearch/S3/BDA/PostgreSQL concerns out of `domain/`.

## Important Runtime Facts

- **Spring Boot does NOT auto-load `.env` files.** The IDE launch command must explicitly export all required env vars (`BEDROCK_REGION=us-west-2`, `RAG_ANSWER_MODEL_ID`, `S3_DOCUMENT_BUCKET`). The `.env` file is a reference template only.
- Both backends use port `8001`; only one can run at a time.
- Java target is temporarily `17`.
- Maven repo override: `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository`
- Frontend dev server: `localhost:8080`. Requires `frontend/.env` and backend CORS (`CorsConfig.java`).
- Answer model default is `qwen.qwen3-235b-a22b-2507-v1:0` in `us-west-2`. `BedrockAnswerGenerationAdapter` uses the `converse` API (not `invokeModel` — third-party models only support `converse`).
- Model IDs are configurable via env vars: `RAG_ANSWER_MODEL_ID`, `RAG_EMBEDDING_MODEL_ID`, `RAG_RERANK_MODEL_ID`. Defaults are set in both `application.yml` and `RagProperties.java` so the backend works even if `.env` is not loaded.
- IDE/terminal startup may not load `backend-java/.env` automatically. Verify env vars are present in the process (check startup log line 1).
- `ensureIndex()` auto-detects and handles missing/invalid/valid index mapping via single GET /_mapping call.
- Use `bash backend-java/diagnose-aws.sh` to verify AWS connectivity.

## Storage And Parsing Reality

Do not rely on outdated assumptions from older Python-only docs.

- Python `api/` upload flow stores incoming files locally and processes them directly.
- Current Java runtime path uses S3-backed document storage for uploaded source files.
- Java BDA parsing expects S3-backed storage/output configuration.
- For `backend-java/`, treat these configuration areas as active:
  - PostgreSQL
  - OpenSearch
  - Bedrock
  - BDA
  - S3 document storage and BDA output prefixes

## Verification Discipline

Never mark migration work complete without running the verification command appropriate to the touched milestone.

Common commands:

```powershell
# PowerShell:
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run
```

```bash
# Bash:
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
curl http://localhost:8001/health
bash backend-java/diagnose-aws.sh
```

## Documentation Maintenance Rule

When migration status, decisions, constraints, or next steps change, update these documents together:

- `control/Prompt.md`
- `control/Plan.md`
- `control/Implement.md`
- `control/Documentation.md`

Do not let `CLAUDE.md` drift back to an outdated Python-only description.

## Safety And Hygiene

- Do not delete or break the Python backend during migration.
- Do not modify the React frontend to accommodate backend contract drift unless the user explicitly changes requirements.
- Do not commit `backend-java/target/`.
- Treat local credential-bearing files and environment examples as sensitive; never copy secrets into docs, commits, or responses.
- The worktree may be dirty. Do not revert unrelated user changes.

## Recommended Default Workflow

For migration work in this repo:

1. Read the `control/` docs first.
2. Determine whether the task belongs to `api/` baseline reference, `backend-java/` implementation, or cutover validation.
3. Preserve frontend contract and Python parity.
4. Run the smallest relevant verification command.
5. Update the root migration documents if status or guidance changed.

## Mental Model

- Python `api/` is the legacy baseline and rollback target.
- `backend-java/` is the nearly completed Spring Boot replacement, now runtime-verified.
- The repository is in cutover-readiness mode, not greenfield development.
