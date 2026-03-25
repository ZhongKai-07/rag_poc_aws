# NEWTON UI Refactoring Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the NEWTON frontend to connect to the real backend APIs used by the `frontend` directory, replacing hardcoded simulations with actual API calls while maintaining the exact data flow, state management, and business rules, without touching the `frontend` or `backend` directories.

**Architecture:** We will update the `ragApi.ts` service in NEWTON to match the real backend endpoints (`/upload_files`, `/processed_files`, `/top_questions_multi`, `/rag_answer`). We will integrate these into `FileParseModal.tsx` for uploading and `MaximizedChat.tsx` for the Q&A loop. We will introduce a document selector in the chat UI to satisfy the requirement of selecting index names before querying.

**Tech Stack:** React 18, Vite 6, Tailwind v4, Lucide React

---

### Task 1: Environment and API Service Setup

**Files:**
- Create/Modify: `NEWTON/.env`
- Modify: `NEWTON/src/app/services/ragApi.ts`
- Modify: `NEWTON/src/app/services/types.ts`

- [ ] **Step 1: Create `.env` file**
Create `NEWTON/.env` and add `VITE_API_BASE_URL=http://localhost:8001` to point to the real backend.

- [ ] **Step 2: Update `types.ts`**
Add the real API response and request interfaces from `frontend/src/pages/QA.tsx` and `Upload.tsx` (e.g., `ProcessedFile`, `SourceDocument`, `RAGResponse`, `TopQuestion`).

- [ ] **Step 3: Implement real API calls in `ragApi.ts`**
Replace the mock API functions with real `fetch` calls to `${import.meta.env.VITE_API_BASE_URL}`:
  - `fetchProcessedFiles()` -> `GET /processed_files`
  - `uploadFiles(files: File[])` -> `POST /upload_files` (loop through files, use `FormData` with `file` and `directory_path` based on current time)
  - `fetchTopQuestions(indexNames: string[])` -> `GET /top_questions_multi`
  - `askQuestion(payload)` -> `POST /rag_answer`

### Task 2: Refactor Upload Logic (FileParseModal)

**Files:**
- Modify: `NEWTON/src/app/components/FileParseModal.tsx`

- [ ] **Step 1: Integrate real `uploadFiles` API**
In `handleConfirmFiles`, replace the `setTimeout` simulation with a call to `uploadFiles` from `ragApi.ts`.
Show the "parsing" status while uploading.
Wait for all uploads to complete, then mark as "success" or "error" based on the response.

- [ ] **Step 2: Update UI state**
Remove the fake generated JSON data (clientName, idNumber, etc.). Instead, since `/upload_files` just returns success/failure and processes the document for RAG, we can simplify the "preview" and "download" steps or skip them, jumping directly to "confirm" (Upload Successful). If the UI strictly needs to show parsed data, we can just show a success summary. To strictly preserve the NEWTON flow, we can display the uploaded file names as "Processed successfully" and skip the fake JSON editing step.

### Task 3: Refactor Q&A Logic (MaximizedChat)

**Files:**
- Modify: `NEWTON/src/app/components/MaximizedChat.tsx`
- Modify: `NEWTON/src/app/components/HotQuestions.tsx` (if needed)

- [ ] **Step 1: State Management for Documents**
Add states for `processedFiles`, `selectedFiles`, `searchMode` (default "mix"), `threshold` (default "0"), `numDocs` (default "3").
Call `fetchProcessedFiles` on mount and populate the state.
Automatically select all files by default, or add a simple "Documents" dropdown in the top controls to let users select/deselect files (to maintain 100% feature parity with the old `QA.tsx`).

- [ ] **Step 2: Integrate `fetchTopQuestions`**
When `selectedFiles` change, call `fetchTopQuestions(indexNames)` and update the `HotQuestions` or the default suggested questions list in the UI.

- [ ] **Step 3: Integrate `askQuestion` API**
In `handleSend` and `handleQuestionClick`, replace the `setTimeout` simulation with the real `askQuestion` call.
Construct the payload using the `selectedFiles` (mapped to `index_names`), `question`, and config states.
Generate a `session_id`.
Set `isLoading` while waiting for the response.

- [ ] **Step 4: Render real Citations**
When the response returns, extract `recall_documents` and `rerank_documents`.
Map them to the `citations` array format used by `MessageWithCitations.tsx` so the UI correctly displays the real evidence documents in the right panel and inline citations.

### Task 4: Documentation

**Files:**
- Create: `NEWTON/docs/UI_COMPONENTS.md`
- Create: `NEWTON/CHANGELOG.md`

- [ ] **Step 1: Write `UI_COMPONENTS.md`**
Document the independent UI components (MaximizedChat, FileParseModal, MessageWithCitations, etc.) and their real API data bindings.

- [ ] **Step 2: Write `CHANGELOG.md`**
Record the migration of the API logic from `frontend` to `NEWTON`, detailing the removal of mock data and the integration of real endpoints.

### Task 5: End-to-End Verification

**Files:**
- No files modified.

- [ ] **Step 1: Verify Build and Run**
Ensure `NEWTON` builds and runs without errors.
Verify that file upload sends a real request to `localhost:8001/upload_files`.
Verify that sending a message sends a real request to `localhost:8001/rag_answer` and displays the answer and citations correctly.
