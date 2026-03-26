# Changelog

## [Unreleased]

### Changed
- **API Integration:** Refactored `src/app/services/ragApi.ts` to replace mocked `setTimeout` responses with real backend API calls.
- **Upload Logic:** Updated `FileParseModal.tsx` to send real `FormData` to the `POST /upload_files` endpoint.
- **Chat Logic:** Updated `MaximizedChat.tsx` to:
  - Fetch available documents via `GET /processed_files`.
  - Fetch contextual hot questions via `GET /top_questions_multi`.
  - Send queries via `POST /rag_answer` with proper payload configuration (`session_id`, `index_names`, thresholds).
  - Dynamically render `recall_documents` as inline citations.
- **Environment Configuration:** Added `.env` with `VITE_API_BASE_URL` pointing to the live backend server.
- **Build Fixes:** Removed broken `figma:asset` imports in `App.tsx`, `FloatingButton.tsx`, and `MaximizedChat.tsx` to ensure successful Vite production builds.

### Removed
- Hardcoded mock data for parsing responses in `FileParseModal.tsx`.
- Simulated timeout responses in `MaximizedChat.tsx`.
