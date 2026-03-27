# Enhanced RAG Batch B Design — Multi-turn Chat, Streaming, Feedback, UX Improvements

**Date:** 2026-03-28
**Status:** Approved
**Scope:** Batch B (experience improvements) of Phase 1 Enhanced RAG

## 1. Background & Goals

Batch A delivered core quality improvements (query rewriting, answer citation, offline evaluation — 86 tests passing). Batch B focuses on **user experience**: multi-turn conversations, streaming output, feedback collection, follow-up suggestions, and citation display polish.

**Batch B delivers six capabilities:**

1. **Multi-turn Conversation** — persistent sessions with sliding window + compression
2. **Streaming Output** — SSE for real-time answer delivery
3. **User Feedback** — thumbs up/down per message with admin view
4. **Suggested Follow-up Questions** — LLM-generated in same prompt
5. **Answer Confidence** — rerank-score-based confidence indicator
6. **Citation Display Optimization** — filename fallback, noise filtering, truncation

**Non-goals for Batch B:**

- Spring AI framework migration (deferred until AWS account switch complete + Spring AI 2.0 GA)
- Langfuse / online observability (deferred until production launch)
- Document parsing quality improvements (deferred to Docling integration)
- Frontend full redesign (only minimal integration for feedback)
- Agent / tool-use capabilities (Phase 2)

## 2. Architecture Approach

**Continue pipeline-inline enhancement** on the existing hexagonal architecture. No new frameworks. New features add domain ports, application services, infrastructure adapters, and Flyway migrations following established patterns.

**Key architectural decisions:**

| # | Decision | Conclusion | Rationale |
|---|----------|------------|-----------|
| 1 | Spring AI | Not introduced in Batch B | AWS account switch pending; Spring AI 2.0 still M3; hand-written adapters more transparent during infrastructure transition |
| 2 | Langfuse | Not introduced in Batch B | POC query volume (~120/day) doesn't justify deployment overhead; RAGAS covers offline evaluation needs |
| 3 | Streaming tech | SSE via `SseEmitter` | Bedrock `converseStream()` maps naturally to SSE; no WebFlux dependency needed; upgrade to WebSocket later if needed |
| 4 | Session storage | PostgreSQL | Existing DB infra; supports browser-close-resume; enables feedback correlation |
| 5 | Conversation memory | Sliding window (5 turns) + LLM compression | Short conversations (80% ≤5 turns) use zero overhead; long conversations get compressed with user notification |

## 3. Multi-turn Conversation

### 3.1 Data Model

```sql
-- Flyway: V5__chat_session.sql
CREATE TABLE chat_session (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    module VARCHAR(64) NOT NULL DEFAULT 'RAG',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Flyway: V6__chat_message.sql
CREATE TABLE chat_message (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,  -- 'USER' or 'ASSISTANT'
    content TEXT NOT NULL,
    citations JSONB,            -- ASSISTANT only
    suggested_questions JSONB,  -- ASSISTANT only
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_session ON chat_message(session_id, created_at);
```

### 3.2 Domain Layer

```java
// domain/chat/ChatSession.java
public record ChatSession(
    UUID id,
    String title,
    String module,
    Instant createdAt,
    Instant updatedAt
) {}

// domain/chat/ChatMessage.java
public record ChatMessage(
    UUID id,
    UUID sessionId,
    String role,          // "USER" or "ASSISTANT"
    String content,
    List<Citation> citations,
    List<String> suggestedQuestions,
    Instant createdAt
) {}

// domain/chat/ChatSessionPort.java
public interface ChatSessionPort {
    ChatSession create(String title, String module);
    Optional<ChatSession> findById(UUID id);
    List<ChatSession> listSessions(int page, int size);
    void updateTitle(UUID id, String title);
    void delete(UUID id);
}

// domain/chat/ChatMessagePort.java
public interface ChatMessagePort {
    void save(ChatMessage message);
    List<ChatMessage> loadRecent(UUID sessionId, int limit);
    int countMessages(UUID sessionId);
    List<ChatMessage> loadAll(UUID sessionId);
}
```

### 3.3 Session Management API (New)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/sessions` | Create new session (auto-title from first query) |
| `GET` | `/sessions` | List sessions (paginated, newest first) |
| `GET` | `/sessions/{id}` | Session detail with message history |
| `DELETE` | `/sessions/{id}` | Delete session and all messages |
| `PATCH` | `/sessions/{id}` | Rename session title |

### 3.4 Conversation Memory: Sliding Window + Compression

```java
// domain/chat/ConversationMemoryPort.java
public interface ConversationMemoryPort {
    /** Load context for prompt injection */
    ConversationContext loadContext(UUID sessionId);
    /** Compress older messages into summary */
    String compressHistory(List<ChatMessage> messages);
}

// domain/chat/ConversationContext.java
public record ConversationContext(
    String formattedHistory,   // Ready to inject into prompt
    boolean compressed         // True if compression was triggered
) {}
```

**Logic:**

```
messageCount = chatMessagePort.countMessages(sessionId)

if messageCount <= 10 (5 turns = 10 messages, user+assistant):
    → Load all messages, format as conversation history
    → compressed = false

if messageCount > 10:
    → Load all messages
    → Compress older messages into summary via LLM
    → Keep last 6 messages (3 turns) as-is
    → Format as: [Summary] + [Recent 3 turns]
    → compressed = true
```

**Compression prompt:**

```
请将以下对话历史压缩为一段简洁的摘要，保留关键信息（问题、答案要点、文档来源）：

{history}

输出摘要（不超过200字）：
```

**Response flag:** `"history_compressed": true` in response, frontend shows notification to user.

### 3.5 Pipeline Integration

Modify `RagQueryApplicationService.Default.handle()`:

```
Before (Batch A):
  query → rewrite → retrieve → rerank → citationAssembly → generate → response

After (Batch B):
  query + sessionId
    → loadContext(sessionId) → get formattedHistory + compressed flag
    → rewrite (unchanged)
    → retrieve → rerank
    → citationAssembly (inject history into prompt context)
    → generate (history + documents + query)
    → save USER message + ASSISTANT message to chat_message
    → response + history_compressed flag
```

History is injected into the prompt between system prompt and document context:

```
System: 你是一个证券公司的COB专家...

对话历史：
{formattedHistory}

相关文档如下...
[1] (AML手册.pdf, 第12页)
...

用户问题：
{query}
```

### 3.6 Modification to `/rag_answer`

The existing `session_id` field in `RagRequest` changes from a client-generated string to a real `UUID` referencing `chat_session`. Backward compatibility:

- If `session_id` is null or empty → create a new session automatically, return `session_id` in response
- If `session_id` is a valid UUID → load session history
- If `session_id` is not a valid UUID (legacy frontend sends random string) → treat as new session

## 4. Streaming Output (SSE)

### 4.1 New Endpoint

| Method | Path | Content-Type | Purpose |
|--------|------|-------------|---------|
| `POST` | `/rag_answer/stream` | `text/event-stream` | Streaming RAG answer |

Existing `POST /rag_answer` preserved for backward compatibility.

### 4.2 SSE Event Format

```
event: token
data: {"content": "根据"}

event: token
data: {"content": "[1]"}

event: token
data: {"content": "，KYC审查"}

...

event: done
data: {"answer": "full answer...", "citations": [...], "suggested_questions": [...], "confidence": "HIGH", "history_compressed": false, "session_id": "uuid"}

-- or on error:

event: error
data: {"message": "Bedrock service timeout"}
```

Three event types:
- `token` — incremental answer text
- `done` — complete structured response (citations, suggestions, confidence computed after full answer)
- `error` — error message, then connection closed

### 4.3 AnswerGenerationPort Extension

```java
// domain/rag/AnswerGenerationPort.java
public interface AnswerGenerationPort {
    String generateAnswer(String query, String formattedContext);              // existing
    void generateAnswerStream(String query, String formattedContext,
                              Consumer<String> tokenConsumer);                 // new
}
```

`BedrockAnswerGenerationAdapter` adds streaming implementation using `bedrockRuntimeClient.converseStream()`, calling `tokenConsumer.accept(token)` per token.

### 4.4 Controller Implementation

```java
@PostMapping(value = "/rag_answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter ragAnswerStream(@RequestBody RagRequest request) {
    SseEmitter emitter = new SseEmitter(60_000L);
    executor.execute(() -> {
        try {
            // 1. Shared pipeline: history → rewrite → retrieve → rerank → citation assembly
            // 2. Stream: generateAnswerStream(tokenConsumer) → send token events
            // 3. After stream complete: parse citations + compute confidence
            // 4. Send done event with full structured data
            // 5. Save messages to DB
            emitter.complete();
        } catch (Exception e) {
            emitter.send(SseEmitter.event().name("error").data(...));
            emitter.completeWithError(e);
        }
    });
    return emitter;
}
```

### 4.5 Pipeline Reuse

Streaming and synchronous share the same pipeline up to answer generation, then fork:

```
Shared: query → history → rewrite → retrieve → rerank → citationAssembly
  ├── Sync:   generateAnswer() → parseJSON → return RagResponse
  └── Stream: generateAnswerStream(tokenConsumer) → collect full text → parseJSON → send done event
```

## 5. User Feedback

### 5.1 Data Model

```sql
-- Flyway: V7__chat_feedback.sql
CREATE TABLE chat_feedback (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    rating VARCHAR(16) NOT NULL,  -- 'THUMBS_UP' or 'THUMBS_DOWN'
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(message_id)  -- one feedback per message, upsert semantics
);

CREATE INDEX idx_chat_feedback_session ON chat_feedback(session_id);
CREATE INDEX idx_chat_feedback_rating ON chat_feedback(rating);
```

### 5.2 Domain Layer

```java
// domain/chat/ChatFeedback.java
public record ChatFeedback(
    UUID id,
    UUID messageId,
    UUID sessionId,
    String rating,     // "THUMBS_UP" or "THUMBS_DOWN"
    String comment,
    Instant createdAt
) {}

// domain/chat/ChatFeedbackPort.java
public interface ChatFeedbackPort {
    void upsert(ChatFeedback feedback);
    Optional<ChatFeedback> findByMessageId(UUID messageId);
    List<ChatFeedback> list(String ratingFilter, int page, int size);
    FeedbackStats stats();
}

public record FeedbackStats(long total, long thumbsUp, long thumbsDown, double approvalRate) {}
```

### 5.3 API

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/sessions/{sessionId}/messages/{messageId}/feedback` | Submit/update feedback |
| `GET` | `/admin/feedback` | List feedback (paginated, filterable by rating) |
| `GET` | `/admin/feedback/stats` | Feedback statistics |

Request body:
```json
{"rating": "thumbs_up", "comment": "答案准确"}
```

### 5.4 Message Response Includes Feedback State

When loading session messages (`GET /sessions/{id}`), each ASSISTANT message includes its feedback:

```json
{
    "id": "msg-uuid",
    "role": "ASSISTANT",
    "content": "根据[1]...",
    "citations": [...],
    "feedback": {"rating": "thumbs_up"}  // or null if no feedback
}
```

### 5.5 Frontend Minimal Integration

Scope for Batch B:
- 👍👎 buttons below each ASSISTANT message
- Click calls `POST .../feedback`
- Already-rated messages show highlighted state (from `feedback` field in API)
- No comment input modal (optional, future)
- No admin feedback UI page (use API directly)

## 6. Suggested Follow-up Questions

### 6.1 Approach: Same-Prompt Generation

Modify the citation prompt template to also request follow-up questions:

```
请根据以上文档回答用户问题。回答时必须使用[编号]标注信息来源。
如果文档中没有相关信息，请说明无法找到答案，不要编造内容。

回答完成后，请根据文档内容和用户问题，生成2-3个用户可能感兴趣的追问建议。

输出格式（严格JSON）：
{"answer": "...", "suggested_questions": ["追问1", "追问2", "追问3"]}

示例：
{"answer": "根据[1]，KYC审查流程要求...", "suggested_questions": ["World-Check匹配后的升级流程是什么？", "KYC审查的时效要求是多久？", "高风险客户的额外审查步骤有哪些？"]}

用户问题：
{query}
```

### 6.2 Impact on Pipeline

- `CitationAssemblyService` modifies prompt template to include JSON output instruction + few-shot example
- LLM output changes from plain text to JSON (`{"answer": "...", "suggested_questions": [...]}`)
- `CitationAssemblyService.parseResponse()` extended: extract `answer` for citation parsing + extract `suggested_questions` list
- Streaming: tokens stream as-is; after full text collected, parse JSON in `done` event

### 6.3 Graceful Degradation

If LLM does not output valid JSON (occasional):
- Treat entire output as the answer text
- Return empty `suggested_questions: []`
- Log warning for monitoring

### 6.4 Domain Model Change

`CitedAnswer` record extended:

```java
public record CitedAnswer(
    String answer,
    List<Citation> citations,
    List<String> suggestedQuestions  // NEW
) {}
```

## 7. Answer Confidence

### 7.1 Calculation

Based on the highest rerank score among source documents — zero LLM cost:

```java
public enum ConfidenceLevel {
    HIGH,    // max rerank score >= 0.8
    MEDIUM,  // max rerank score >= 0.5 and < 0.8
    LOW      // max rerank score < 0.5 or no rerank results
}
```

Computed in `RagQueryApplicationService` after reranking, before answer generation.

### 7.2 Response Field

```json
{"confidence": "HIGH"}
```

Frontend can display as badge/label. LOW confidence can trigger a disclaimer: "以下回答仅供参考，建议人工复核".

## 8. Citation Display Optimization

### 8.1 Filename Fallback via PostgreSQL

When `metadata.filename` is missing from OpenSearch (legacy indexed documents):

```java
// In CitationAssemblyService:
String filename = extractFromMetadata(metadata, "filename");
if (filename == null) {
    filename = documentRegistryPort.findFilenameByIndexName(indexName)
                                   .orElse(indexName);
}
```

`DocumentRegistryPort` adds: `Optional<String> findFilenameByIndexName(String indexName)` — queries `document_file` table.

### 8.2 OCR Noise Filtering

Light-weight cleanup in `CitationAssemblyService` before building excerpt:

```java
// Remove consecutive special characters (OCR artifacts)
text = text.replaceAll("[<>*#=]{3,}", "");
// Remove Cyrillic character noise (BDA OCR misrecognition)
text = text.replaceAll("[\\p{IsCyrillic}]+", "");
// Collapse whitespace
text = text.replaceAll("\\s{2,}", " ").trim();
```

### 8.3 Excerpt Truncation

Citation card excerpt limited to 150 characters:

```java
String excerpt = cleanedText.length() > 150
    ? cleanedText.substring(0, 150) + "..."
    : cleanedText;
```

Frontend provides "expand" option to view full content via `source_documents[].page_content`.

## 9. API Response Changes (Backward Compatible)

### 9.1 Enhanced RagResponse

```json
{
    "answer": "根据[1]，KYC审查流程要求...",
    "citations": [
        {"index": 1, "filename": "AML手册.pdf", "page_number": 12,
         "section_path": "第三章/KYC审查", "excerpt": "KYC审查流程要求..."}
    ],
    "suggested_questions": ["World-Check匹配后升级流程？", "KYC审查时效？"],
    "confidence": "HIGH",
    "history_compressed": false,
    "session_id": "uuid-of-session",
    "source_documents": [...],
    "recall_documents": [...],
    "rerank_documents": [...]
}
```

New fields: `suggested_questions`, `confidence`, `history_compressed`, `session_id`. All additive — old frontend ignores them.

### 9.2 Session Detail Response

```json
{
    "id": "session-uuid",
    "title": "World-Check匹配处理流程",
    "module": "cob",
    "created_at": "2026-03-28T10:00:00Z",
    "messages": [
        {"id": "msg1", "role": "USER", "content": "World-Check匹配怎么处理？", "created_at": "..."},
        {"id": "msg2", "role": "ASSISTANT", "content": "根据[1]...",
         "citations": [...], "suggested_questions": [...],
         "feedback": {"rating": "thumbs_up"}, "created_at": "..."}
    ]
}
```

## 10. File Inventory

### New Files

| Layer | File | Purpose |
|-------|------|---------|
| domain/chat | `ChatSession.java` | Session value object |
| domain/chat | `ChatMessage.java` | Message value object |
| domain/chat | `ChatFeedback.java` | Feedback value object |
| domain/chat | `ChatSessionPort.java` | Session persistence port |
| domain/chat | `ChatMessagePort.java` | Message persistence port |
| domain/chat | `ChatFeedbackPort.java` | Feedback persistence port |
| domain/chat | `ConversationMemoryPort.java` | Memory load + compress port |
| domain/chat | `ConversationContext.java` | Memory context value object |
| domain/rag | `ConfidenceLevel.java` | Confidence enum |
| application/chat | `ChatSessionApplicationService.java` | Session CRUD orchestration |
| application/chat | `ConversationMemoryService.java` | Sliding window + compression logic |
| api/chat | `ChatSessionController.java` | Session management REST endpoints |
| api/chat/dto | `SessionDto.java`, `MessageDto.java`, `FeedbackDto.java` | API DTOs |
| api/rag/dto | `SuggestedQuestionDto.java` | (or inline as List<String>) |
| infrastructure/persistence/entity | `ChatSessionEntity.java` | JPA entity |
| infrastructure/persistence/entity | `ChatMessageEntity.java` | JPA entity |
| infrastructure/persistence/entity | `ChatFeedbackEntity.java` | JPA entity |
| infrastructure/persistence/repository | `ChatSessionJpaRepository.java` | JPA repo |
| infrastructure/persistence/repository | `ChatMessageJpaRepository.java` | JPA repo |
| infrastructure/persistence/repository | `ChatFeedbackJpaRepository.java` | JPA repo |
| infrastructure/persistence | `ChatSessionPersistenceAdapter.java` | Port impl |
| infrastructure/persistence | `ChatMessagePersistenceAdapter.java` | Port impl |
| infrastructure/persistence | `ChatFeedbackPersistenceAdapter.java` | Port impl |
| infrastructure/bedrock | `BedrockConversationMemoryAdapter.java` | LLM compression impl |
| resources/db/migration | `V5__chat_session.sql` | Session table |
| resources/db/migration | `V6__chat_message.sql` | Message table |
| resources/db/migration | `V7__chat_feedback.sql` | Feedback table |

### Modified Files

| File | Change |
|------|--------|
| `RagQueryApplicationService.java` | Inject conversation history; save messages; add confidence + suggestions to QueryResult |
| `CitationAssemblyService.java` | New prompt template (JSON output + suggested questions); noise filtering; filename fallback; excerpt truncation |
| `CitedAnswer.java` | Add `suggestedQuestions` field |
| `AnswerGenerationPort.java` | Add `generateAnswerStream()` method |
| `BedrockAnswerGenerationAdapter.java` | Implement `converseStream()` streaming |
| `RagController.java` | Add `/rag_answer/stream` SSE endpoint |
| `RagResponse.java` | Add `suggested_questions`, `confidence`, `history_compressed`, `session_id` fields |
| `RagRequest.java` | `session_id` becomes UUID reference to chat_session |
| `DocumentRegistryPort.java` | Add `findFilenameByIndexName()` method |
| `DocumentRegistryPersistenceAdapter.java` | Implement filename lookup |
| `ApplicationWiringConfig.java` | Wire new beans |
| `application.yml` | Add conversation memory config (window size, compression threshold) |
| Frontend (`QA.tsx` or NEWTON) | Minimal: 👍👎 buttons, feedback state display |

### Unchanged

- BDA / Docling parsing pipeline
- Query rewrite strategies (COB / Collateral)
- Rerank logic
- RAGAS evaluation module
- OpenSearch indexing pipeline
- Existing 10 REST endpoint signatures (additive changes only)

## 11. Key Decisions Summary

| # | Decision | Conclusion |
|---|----------|------------|
| 1 | Spring AI | Not introduced; deferred until AWS account switch + 2.0 GA |
| 2 | Langfuse | Not introduced; deferred until production launch |
| 3 | Conversation memory | Sliding window (5 turns) + LLM compression + user notification |
| 4 | Session storage | PostgreSQL with JPA (3 new tables) |
| 5 | Streaming | SSE via SseEmitter + Bedrock converseStream() |
| 6 | Suggested questions | Same-prompt JSON generation, graceful degradation |
| 7 | Confidence | Rerank-score-based: HIGH/MEDIUM/LOW |
| 8 | Citation filename | PostgreSQL fallback when OpenSearch metadata missing |
| 9 | Citation noise | Regex cleanup of OCR artifacts + 150-char truncation |
| 10 | User feedback | Message-level thumbs up/down, upsert, minimal frontend |
| 11 | New endpoint | POST /rag_answer/stream (SSE), existing /rag_answer preserved |

## 12. Future: Batch B+ / Phase 2 (Not This Design)

- Spring AI framework migration (when prerequisites met)
- Langfuse full-chain tracing + dashboard
- Docling parser integration (better OCR quality)
- Agent / tool-use capabilities
- Frontend full NEWTON migration
