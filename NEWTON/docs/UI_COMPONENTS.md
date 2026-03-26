# NEWTON UI Components Documentation

## Overview
This document outlines the UI components used in the NEWTON project. The UI architecture has been refactored to consume real API endpoints while maintaining a modern, floating, and modal-based interaction paradigm.

## Core Components

### 1. `App.tsx`
- **Responsibility:** The main entry point. It manages the global state for the desktop layout, including the background image and the toggle state between the `FloatingButton` and `MaximizedChat`.
- **Key Interactions:** 
  - Double-clicking the `FloatingButton` opens the `MaximizedChat`.
  - Closing the `MaximizedChat` returns the user to the `FloatingButton` view.

### 2. `FloatingButton.tsx`
- **Responsibility:** Acts as a minimized entry point for the AI assistant.
- **Features:** Draggable across the screen. Double-click to expand to full view.

### 3. `MaximizedChat.tsx`
- **Responsibility:** The main Q&A interface.
- **Key Features:**
  - **Document Selection:** Automatically fetches `processed_files` on mount using `fetchProcessedFiles()` from the backend.
  - **Question Submission:** Calls `askRealQuestion()` (mapping to `/rag_answer`) with the selected document index names.
  - **Citation Handling:** Receives `recall_documents` from the backend and maps them to inline UI citations.
  - **Hot Questions:** Fetches dynamic suggested questions based on selected documents using `/top_questions_multi`.

### 4. `FileParseModal.tsx`
- **Responsibility:** Handles document upload and parsing.
- **Key Features:**
  - **File Upload:** Uses `uploadRealFiles()` to send documents via `POST /upload_files` with `FormData`.
  - **State Management:** Tracks upload progress, success, and error states based on real HTTP responses.

### 5. `MessageWithCitations.tsx`
- **Responsibility:** Renders chat messages with inline citation markers.
- **Features:** Parses `{cite:X}` syntax in bot responses and displays clickable references that open `CitationModal`.

## Data Flow
- **API Service:** `src/app/services/ragApi.ts` handles all HTTP requests to the backend (e.g., `http://localhost:8001`).
- **State Management:** React `useState` and `useEffect` hooks manage data binding, ensuring synchronization between backend data (documents, top questions, chat responses) and the UI components.
