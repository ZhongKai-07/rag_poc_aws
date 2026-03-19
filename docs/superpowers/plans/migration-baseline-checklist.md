# Migration Baseline Checklist

## Task 1 Scope

This checklist freezes the Python backend behavior that the layered Spring Boot backend must preserve while keeping the React frontend unchanged.

Current isolated migration workspace:

- `C:\Users\zhong kai\.codex\worktrees\ff34\huatai_rag_github_share`

Python baseline references:

- `api/api.py`
- `api/RAG_System.py`
- `api/document_processing.py`
- `api/opensearch_search.py`

## Frontend Contract To Preserve

- `POST /upload_files`
- `POST /rag_answer`
- `GET /processed_files`
- `GET /get_index/{filename}`
- `GET /top_questions/{index_name}`
- `GET /top_questions_multi`
- `GET /health`

## Request And Response Shape Compatibility

- `POST /rag_answer` request must continue to accept:
  - `session_id`
  - `index_names`
  - `query`
  - `module`
  - `vec_docs_num`
  - `txt_docs_num`
  - `vec_score_threshold`
  - `text_score_threshold`
  - `rerank_score_threshold`
  - `search_method`
- `POST /rag_answer` response must continue to return:
  - `answer`
  - `source_documents`
  - `recall_documents`
  - `rerank_documents`
- `source_documents` and `rerank_documents` entries must preserve:
  - `page_content`
  - `score`
  - `rerank_score`
- `recall_documents` entries must preserve:
  - `page_content`
  - `score`
- `GET /processed_files` response must preserve:
  - `status`
  - `files[].filename`
  - `files[].index_name`
- `GET /get_index/{filename}` response must preserve:
  - success shape: `status`, `index_name`
  - missing-file shape: `status`, `message`
- `GET /top_questions/{index_name}` and `GET /top_questions_multi` must preserve:
  - `status`
  - `questions[].question`
  - `questions[].count`
- `GET /health` must preserve:
  - `{"status":"healthy"}`

## Behavioral Invariants To Preserve

- Multi-index query joining:
  - Python joins `index_names` with commas before invoking RAG retrieval.
- Vector, text, and mix retrieval:
  - `search_method` supports `vector`, `text`, and mixed retrieval behavior.
- Deterministic index naming:
  - Existing processed-file mappings must remain resolvable and future Java ingestion must preserve Python-compatible deterministic naming.
- Processed file listing:
  - Response is built from `processed_files.txt`, supporting both JSON-line format and legacy comma-delimited lines.
- Top question aggregation:
  - Per-index history is stored independently and `top_questions_multi` aggregates counts across comma-separated indices.
- Rerank threshold behavior:
  - Results are filtered after reranking by `rerank_score_threshold`.
- Response shape compatibility:
  - `source_documents` mirrors filtered rerank results in the Python implementation.

## Baseline Regression Dataset

Representative baseline PDFs identified from `api/processed_files.txt`:

- `(1) OTCD - ISDA & CSA in same doc.pdf` -> `2f295fa6`
- `(2) OTCD - ISDA.pdf` -> `dca3cd03`
- `PRC Client.pdf` -> `2374dcf7`
- `Onboarding Decision Chart.pdf` -> `32a592c0`

Regression question cases are recorded in:

- `backend-java/src/test/resources/fixtures/regression/questions.csv`

Dataset storage rule:

- Actual PDF binaries are intentionally not committed in the current repository snapshot.
- Place the external baseline PDFs into `backend-java/src/test/resources/fixtures/regression/documents/` before running full regression and ingestion parity tests.
