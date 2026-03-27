# Enhanced RAG Batch B Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multi-turn conversation (persistent sessions + sliding window memory), SSE streaming, user feedback, suggested follow-ups, answer confidence, and citation display improvements to the existing RAG pipeline.

**Architecture:** Pipeline-inline enhancement on existing hexagonal architecture. Three new PostgreSQL tables (chat_session, chat_message, chat_feedback) via Flyway. New domain ports for chat/feedback/compression. SSE via SseEmitter + Bedrock converseStream(). No new frameworks.

**Tech Stack:** Java 17, Spring Boot 3.4, AWS Bedrock Converse/ConverseStream API, PostgreSQL, SseEmitter (SSE)

**Spec:** `docs/superpowers/specs/2026-03-28-enhanced-rag-batch-b-design.md`

**Key codebase notes:**
- `RagQueryApplicationService` is an **interface** with `final class Default` inner class (line 64)
- `RagRequest.sessionId` has `@NotBlank` that must be removed
- `CitationAssemblyService` already has `assemble()` (line 32) and `parseResponse()` (line 63)
- Flyway migrations currently end at V4. New migrations start at V5 (adjust if parallel work lands first)
- Existing test count: 86

---

## Chunk 1: Database + Domain Value Objects

### Task 1: Flyway Migrations — chat_session, chat_message, chat_feedback

**Files:**
- Create: `backend-java/src/main/resources/db/migration/V5__chat_session.sql`
- Create: `backend-java/src/main/resources/db/migration/V6__chat_message.sql`
- Create: `backend-java/src/main/resources/db/migration/V7__chat_feedback.sql`

- [ ] **Step 1: Create V5__chat_session.sql**

```sql
CREATE TABLE chat_session (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    module VARCHAR(64) NOT NULL DEFAULT 'RAG',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Create V6__chat_message.sql**

**Note:** Use `TEXT` instead of `JSONB` for H2 test compatibility. Existing V1-V4 migrations avoid JSONB. The JPA entity can use `@Column(columnDefinition = "jsonb")` for PostgreSQL production, but the migration SQL must work on both H2 (tests) and PostgreSQL (prod).

```sql
CREATE TABLE chat_message (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    citations TEXT,
    suggested_questions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_session ON chat_message(session_id, created_at);
```

- [ ] **Step 3: Create V7__chat_feedback.sql**

```sql
CREATE TABLE chat_feedback (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    rating VARCHAR(16) NOT NULL,
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(message_id)
);

CREATE INDEX idx_chat_feedback_session ON chat_feedback(session_id);
CREATE INDEX idx_chat_feedback_rating ON chat_feedback(rating);
```

- [ ] **Step 4: Run tests to verify migrations apply on H2**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test`
Expected: All 86 existing tests PASS (H2 applies migrations)

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/resources/db/migration/V5__chat_session.sql \
       backend-java/src/main/resources/db/migration/V6__chat_message.sql \
       backend-java/src/main/resources/db/migration/V7__chat_feedback.sql
git commit -m "feat: add Flyway migrations for chat session, message, and feedback tables"
```

---

### Task 2: Domain Value Objects — Chat

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ChatSession.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ChatMessage.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ChatFeedback.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ConversationContext.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/FeedbackStats.java`
- Test: `backend-java/src/test/java/com/huatai/rag/domain/ChatDomainTest.java`

- [ ] **Step 1: Write test for domain value objects**

```java
package com.huatai.rag.domain;

import com.huatai.rag.domain.chat.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ChatDomainTest {

    @Test
    void chatSession_holds_all_fields() {
        var id = UUID.randomUUID();
        var now = Instant.now();
        var session = new ChatSession(id, "Test Title", "cob", now, now);
        assertThat(session.id()).isEqualTo(id);
        assertThat(session.title()).isEqualTo("Test Title");
        assertThat(session.module()).isEqualTo("cob");
    }

    @Test
    void chatMessage_holds_all_fields() {
        var msg = new ChatMessage(UUID.randomUUID(), UUID.randomUUID(),
                "USER", "hello", null, null, Instant.now());
        assertThat(msg.role()).isEqualTo("USER");
        assertThat(msg.citations()).isNull();
    }

    @Test
    void chatFeedback_holds_all_fields() {
        var fb = new ChatFeedback(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "THUMBS_UP", "good answer", Instant.now());
        assertThat(fb.rating()).isEqualTo("THUMBS_UP");
    }

    @Test
    void conversationContext_holds_history_and_compression_flag() {
        var ctx = new ConversationContext("history text", true);
        assertThat(ctx.formattedHistory()).isEqualTo("history text");
        assertThat(ctx.compressed()).isTrue();
    }

    @Test
    void feedbackStats_computes_approval_rate() {
        var stats = new FeedbackStats(100, 80, 20, 0.8);
        assertThat(stats.approvalRate()).isEqualTo(0.8);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Create all domain value objects**

```java
// ChatSession.java
package com.huatai.rag.domain.chat;
import java.time.Instant;
import java.util.UUID;
public record ChatSession(UUID id, String title, String module,
                          Instant createdAt, Instant updatedAt) {}

// ChatMessage.java
package com.huatai.rag.domain.chat;
import com.huatai.rag.domain.rag.Citation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ChatMessage(UUID id, UUID sessionId, String role, String content,
                          List<Citation> citations, List<String> suggestedQuestions,
                          Instant createdAt) {}

// ChatFeedback.java
package com.huatai.rag.domain.chat;
import java.time.Instant;
import java.util.UUID;
public record ChatFeedback(UUID id, UUID messageId, UUID sessionId,
                           String rating, String comment, Instant createdAt) {}

// ConversationContext.java
package com.huatai.rag.domain.chat;
public record ConversationContext(String formattedHistory, boolean compressed) {}

// FeedbackStats.java
package com.huatai.rag.domain.chat;
public record FeedbackStats(long total, long thumbsUp, long thumbsDown,
                            double approvalRate) {}
```

- [ ] **Step 4: Run test — expect PASS (5 tests)**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/chat/ \
       backend-java/src/test/java/com/huatai/rag/domain/ChatDomainTest.java
git commit -m "feat: add chat domain value objects"
```

---

### Task 3: Domain Ports — Chat, Feedback, Compression

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ChatSessionPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ChatMessagePort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/ChatFeedbackPort.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/chat/HistoryCompressorPort.java`

- [ ] **Step 1: Create all port interfaces**

```java
// ChatSessionPort.java
package com.huatai.rag.domain.chat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ChatSessionPort {
    ChatSession create(String title, String module);
    Optional<ChatSession> findById(UUID id);
    List<ChatSession> listSessions(int page, int size);
    void updateTitle(UUID id, String title);
    void delete(UUID id);
}

// ChatMessagePort.java
package com.huatai.rag.domain.chat;
import java.util.List;
import java.util.UUID;
public interface ChatMessagePort {
    void save(ChatMessage message);
    List<ChatMessage> loadRecent(UUID sessionId, int limit);
    int countMessages(UUID sessionId);
    List<ChatMessage> loadAll(UUID sessionId);
}

// ChatFeedbackPort.java
package com.huatai.rag.domain.chat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ChatFeedbackPort {
    void upsert(ChatFeedback feedback);
    Optional<ChatFeedback> findByMessageId(UUID messageId);
    List<ChatFeedback> list(String ratingFilter, int page, int size);
    FeedbackStats stats();
}

// HistoryCompressorPort.java
package com.huatai.rag.domain.chat;
import java.util.List;
public interface HistoryCompressorPort {
    String compress(List<ChatMessage> messages);
}
```

- [ ] **Step 2: Run compile to verify interfaces are valid**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/chat/
git commit -m "feat: add chat domain ports"
```

---

### Task 4: ConfidenceLevel Enum + CitedAnswer Extension

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/domain/rag/ConfidenceLevel.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/domain/rag/CitedAnswer.java` (line 1-8)
- Test: `backend-java/src/test/java/com/huatai/rag/domain/ConfidenceAndCitedAnswerTest.java`

- [ ] **Step 1: Write test**

```java
package com.huatai.rag.domain;

import com.huatai.rag.domain.rag.ConfidenceLevel;
import com.huatai.rag.domain.rag.CitedAnswer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceAndCitedAnswerTest {

    @Test
    void confidenceLevel_fromScore() {
        assertThat(ConfidenceLevel.fromScore(0.9)).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(ConfidenceLevel.fromScore(0.6)).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(ConfidenceLevel.fromScore(0.3)).isEqualTo(ConfidenceLevel.LOW);
        assertThat(ConfidenceLevel.fromScore(null)).isEqualTo(ConfidenceLevel.LOW);
    }

    @Test
    void citedAnswer_includes_suggestedQuestions() {
        var ca = new CitedAnswer("answer", List.of(), List.of("q1", "q2"));
        assertThat(ca.suggestedQuestions()).containsExactly("q1", "q2");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Create ConfidenceLevel and update CitedAnswer**

```java
// ConfidenceLevel.java
package com.huatai.rag.domain.rag;

public enum ConfidenceLevel {
    HIGH, MEDIUM, LOW;

    public static ConfidenceLevel fromScore(Double maxRerankScore) {
        if (maxRerankScore == null) return LOW;
        if (maxRerankScore >= 0.8) return HIGH;
        if (maxRerankScore >= 0.5) return MEDIUM;
        return LOW;
    }
}
```

Update `CitedAnswer.java` — add `suggestedQuestions` field:
```java
public record CitedAnswer(
        String answer,
        List<Citation> citations,
        List<String> suggestedQuestions
) {}
```

**Important:** This will break all existing call sites that construct `CitedAnswer` with 2 args. Files to fix:
- `CitationAssemblyService.parseResponse()` (line 77) — add `List.of()` as third arg
- `CitationValueObjectsTest.java` (line 31) — update test to 3-arg constructor
- `CitationAssemblyServiceTest.java` — any test asserting on CitedAnswer construction

- [ ] **Step 4: Fix compilation errors in all affected files listed above**
- [ ] **Step 5: Run full test suite — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/rag/ConfidenceLevel.java \
       backend-java/src/main/java/com/huatai/rag/domain/rag/CitedAnswer.java \
       backend-java/src/test/java/com/huatai/rag/domain/ \
       backend-java/src/main/java/com/huatai/rag/application/rag/CitationAssemblyService.java \
       backend-java/src/test/java/com/huatai/rag/application/
git commit -m "feat: add ConfidenceLevel enum and extend CitedAnswer with suggestedQuestions"
```

---

### Task 5: JPA Entities + Repositories + Persistence Adapters

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/ChatSessionEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/ChatMessageEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/ChatFeedbackEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/ChatSessionJpaRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/ChatMessageJpaRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/ChatFeedbackJpaRepository.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/ChatSessionPersistenceAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/ChatMessagePersistenceAdapter.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/ChatFeedbackPersistenceAdapter.java`
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/ChatPersistenceTest.java`

- [ ] **Step 1: Create JPA Entities** (follow existing patterns from `DocumentFileEntity.java`, `QuestionHistoryEntity.java`)

Key patterns to follow:
- `@Entity`, `@Table(name = "...")`
- UUID id with `@Id`
- `@Column` annotations matching SQL DDL
- Timestamp fields with `@Column(name = "created_at")`
- **JSON columns** (`citations`, `suggested_questions` in `ChatMessageEntity`): Use `@Column(columnDefinition = "text")` with a custom `AttributeConverter<List<...>, String>` that serializes/deserializes JSON via Jackson `ObjectMapper`. No existing entity uses JSON columns, so this is a new pattern.

- [ ] **Step 2: Create JPA Repositories** (Spring Data interfaces)

```java
// ChatSessionJpaRepository.java
public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}

// ChatMessageJpaRepository.java
public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAt(UUID sessionId);
    List<ChatMessageEntity> findTop10BySessionIdOrderByCreatedAtDesc(UUID sessionId);
    int countBySessionId(UUID sessionId);
}

// ChatFeedbackJpaRepository.java
public interface ChatFeedbackJpaRepository extends JpaRepository<ChatFeedbackEntity, UUID> {
    Optional<ChatFeedbackEntity> findByMessageId(UUID messageId);
}
```

- [ ] **Step 3: Create Persistence Adapters** (implement domain ports, convert between entities and records)

- [ ] **Step 4: Write test for persistence adapter**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ChatPersistenceTest {
    @Autowired ChatSessionJpaRepository sessionRepo;
    @Autowired ChatMessageJpaRepository messageRepo;

    @Test
    void save_and_load_session() { ... }

    @Test
    void save_and_load_messages() { ... }

    @Test
    void count_messages_by_session() { ... }
}
```

- [ ] **Step 5: Run tests — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/ \
       backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/ChatPersistenceTest.java
git commit -m "feat: add JPA entities, repositories, and persistence adapters for chat"
```

---

## Chunk 2: Conversation Memory + Session API

### Task 6: ConversationMemoryService (Application Layer)

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/application/chat/ConversationMemoryService.java`
- Test: `backend-java/src/test/java/com/huatai/rag/application/ConversationMemoryServiceTest.java`

- [ ] **Step 1: Write test**

```java
package com.huatai.rag.application;

import com.huatai.rag.application.chat.ConversationMemoryService;
import com.huatai.rag.domain.chat.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConversationMemoryServiceTest {

    @Test
    void returns_empty_context_for_new_session() {
        var msgPort = mock(ChatMessagePort.class);
        when(msgPort.countMessages(any())).thenReturn(0);
        var service = new ConversationMemoryService(msgPort, mock(HistoryCompressorPort.class));

        var ctx = service.loadContext(UUID.randomUUID());
        assertThat(ctx.formattedHistory()).isEmpty();
        assertThat(ctx.compressed()).isFalse();
    }

    @Test
    void returns_full_history_when_within_window() {
        var sessionId = UUID.randomUUID();
        var msgPort = mock(ChatMessagePort.class);
        when(msgPort.countMessages(sessionId)).thenReturn(4); // 2 turns
        when(msgPort.loadAll(sessionId)).thenReturn(List.of(
                new ChatMessage(UUID.randomUUID(), sessionId, "USER", "q1", null, null, Instant.now()),
                new ChatMessage(UUID.randomUUID(), sessionId, "ASSISTANT", "a1", null, null, Instant.now())
        ));
        var service = new ConversationMemoryService(msgPort, mock(HistoryCompressorPort.class));

        var ctx = service.loadContext(sessionId);
        assertThat(ctx.formattedHistory()).contains("q1").contains("a1");
        assertThat(ctx.compressed()).isFalse();
    }

    @Test
    void compresses_when_exceeds_window() {
        var sessionId = UUID.randomUUID();
        var msgPort = mock(ChatMessagePort.class);
        when(msgPort.countMessages(sessionId)).thenReturn(12); // > 10
        var messages = new ArrayList<ChatMessage>();
        for (int i = 0; i < 12; i++) {
            messages.add(new ChatMessage(UUID.randomUUID(), sessionId,
                    i % 2 == 0 ? "USER" : "ASSISTANT", "msg" + i, null, null, Instant.now()));
        }
        when(msgPort.loadAll(sessionId)).thenReturn(messages);

        var compressor = mock(HistoryCompressorPort.class);
        when(compressor.compress(anyList())).thenReturn("compressed summary");
        var service = new ConversationMemoryService(msgPort, compressor);

        var ctx = service.loadContext(sessionId);
        assertThat(ctx.compressed()).isTrue();
        assertThat(ctx.formattedHistory()).contains("compressed summary");
        verify(compressor).compress(anyList());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement ConversationMemoryService**

```java
package com.huatai.rag.application.chat;

import com.huatai.rag.domain.chat.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConversationMemoryService {

    private final ChatMessagePort chatMessagePort;
    private final HistoryCompressorPort compressorPort;
    private final int windowSize;   // configurable, default 10
    private final int keepRecent;   // configurable, default 6

    public ConversationMemoryService(ChatMessagePort chatMessagePort,
                                     HistoryCompressorPort compressorPort,
                                     int windowSize, int keepRecent) {
        this.chatMessagePort = chatMessagePort;
        this.compressorPort = compressorPort;
        this.windowSize = windowSize;
        this.keepRecent = keepRecent;
    }

    public ConversationContext loadContext(UUID sessionId) {
        int count = chatMessagePort.countMessages(sessionId);
        if (count == 0) {
            return new ConversationContext("", false);
        }

        List<ChatMessage> allMessages = chatMessagePort.loadAll(sessionId);

        if (count <= WINDOW_SIZE) {
            return new ConversationContext(formatMessages(allMessages), false);
        }

        // Compress older messages, keep recent
        List<ChatMessage> olderMessages = allMessages.subList(0, allMessages.size() - KEEP_RECENT);
        List<ChatMessage> recentMessages = allMessages.subList(allMessages.size() - KEEP_RECENT, allMessages.size());

        String summary = compressorPort.compress(olderMessages);
        String formatted = "[对话摘要]: " + summary + "\n\n" + formatMessages(recentMessages);
        return new ConversationContext(formatted, true);
    }

    private String formatMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> (m.role().equals("USER") ? "用户: " : "助手: ") + m.content())
                .collect(Collectors.joining("\n"));
    }
}
```

- [ ] **Step 4: Run test — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/chat/ConversationMemoryService.java \
       backend-java/src/test/java/com/huatai/rag/application/ConversationMemoryServiceTest.java
git commit -m "feat: add ConversationMemoryService with sliding window and compression"
```

---

### Task 7: BedrockConversationMemoryAdapter (Infrastructure)

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockConversationMemoryAdapter.java`
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/BedrockConversationMemoryAdapterTest.java`

- [ ] **Step 1: Write test (mocked Bedrock)**

Test that the adapter calls Bedrock converse with the compression prompt and returns the summary text.

- [ ] **Step 2: Run test — expect FAIL**
- [ ] **Step 3: Implement adapter** — follows same pattern as `BedrockAnswerGenerationAdapter`: call `bedrockRuntimeClient.converse()` with system prompt + compression prompt, extract text from response.
- [ ] **Step 4: Run test — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockConversationMemoryAdapter.java \
       backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/BedrockConversationMemoryAdapterTest.java
git commit -m "feat: add Bedrock conversation memory compression adapter"
```

---

### Task 8: ChatSessionApplicationService + ChatSessionController

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/application/chat/ChatSessionApplicationService.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/chat/ChatSessionController.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/chat/dto/SessionDto.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/chat/dto/MessageDto.java`
- Create: `backend-java/src/main/java/com/huatai/rag/api/chat/dto/FeedbackDto.java`
- Test: `backend-java/src/test/java/com/huatai/rag/api/ChatSessionControllerContractTest.java`

- [ ] **Step 1: Create ChatSessionApplicationService**

Orchestrates session CRUD: create (with auto-title from first 50 chars), find, list, rename, delete. Delegates to `ChatSessionPort` and `ChatMessagePort`.

- [ ] **Step 2: Create DTOs**

```java
// SessionDto.java — session list item
// MessageDto.java — message in session detail (includes feedback state)
// FeedbackDto.java — feedback request body
```

- [ ] **Step 3: Create ChatSessionController**

5 endpoints per spec Section 3.3:
- `POST /sessions`
- `GET /sessions`
- `GET /sessions/{id}`
- `DELETE /sessions/{id}`
- `PATCH /sessions/{id}`

- [ ] **Step 4: Write contract test**

```java
@WebMvcTest(ChatSessionController.class)
class ChatSessionControllerContractTest {
    @MockBean ChatSessionApplicationService service;

    @Test void createSession_returns201() { ... }
    @Test void listSessions_returns200() { ... }
    @Test void getSession_returns200_with_messages() { ... }
    @Test void deleteSession_returns204() { ... }
    @Test void renameSession_returns200() { ... }
}
```

- [ ] **Step 5: Run tests — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/chat/ \
       backend-java/src/main/java/com/huatai/rag/api/chat/ \
       backend-java/src/test/java/com/huatai/rag/api/ChatSessionControllerContractTest.java
git commit -m "feat: add session management API (CRUD + message history)"
```

---

## Chunk 3: Streaming Output

### Task 9: AnswerGenerationPort Streaming Extension

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/domain/rag/AnswerGenerationPort.java` (line 5)
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java` (add streaming method)
- Test: `backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/BedrockStreamingTest.java`

- [ ] **Step 1: Add streaming method to AnswerGenerationPort**

```java
// Add to AnswerGenerationPort.java:
void generateAnswerStream(String query, String formattedContext,
                          Consumer<String> tokenConsumer);
```

- [ ] **Step 2: Add default no-op implementation in adapter** (to keep tests passing while we implement)

```java
@Override
public void generateAnswerStream(String query, String formattedContext,
                                 Consumer<String> tokenConsumer) {
    // Fallback: generate synchronously and emit as single token
    String answer = generateAnswer(query, formattedContext);
    tokenConsumer.accept(answer);
}
```

- [ ] **Step 3: Write test for streaming**

```java
class BedrockStreamingTest {
    @Test
    void streamingFallback_emits_full_answer_as_single_token() { ... }
}
```

- [ ] **Step 4: Implement real converseStream() call** — use `bedrockRuntimeClient.converseStream()`, iterate response stream, extract text deltas, call `tokenConsumer.accept()` per token.

- [ ] **Step 5: Fix all compilation errors in tests** — any test faking `AnswerGenerationPort` needs the new method.

- [ ] **Step 6: Run full test suite — expect PASS**
- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/rag/AnswerGenerationPort.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java \
       backend-java/src/test/
git commit -m "feat: add streaming answer generation via Bedrock converseStream()"
```

---

### Task 10: SSE Endpoint + Streaming Thread Pool

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java` (add streaming endpoint)
- Modify: `backend-java/src/main/resources/application.yml` (add streaming config)
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/StreamingConfig.java`

- [ ] **Step 1: Create StreamingConfig with dedicated TaskExecutor**

```java
@Configuration
public class StreamingConfig {
    @Bean("streamingExecutor")
    public TaskExecutor streamingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: Add streaming config to application.yml**

```yaml
huatai:
  streaming:
    core-pool-size: ${STREAMING_CORE_POOL:4}
    max-pool-size: ${STREAMING_MAX_POOL:8}
    queue-capacity: ${STREAMING_QUEUE:50}
    timeout: ${STREAMING_TIMEOUT:60s}
```

- [ ] **Step 3: Add SSE endpoint to RagController**

```java
@PostMapping(value = "/rag_answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter ragAnswerStream(@RequestBody RagRequest request) {
    SseEmitter emitter = new SseEmitter(60_000L);
    streamingExecutor.execute(() -> {
        try {
            // Shared pipeline (reuse logic from handle())
            // Stream tokens via emitter.send()
            // Send done event with full structured data
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", e.getMessage())));
            } catch (IOException ignored) {}
            emitter.completeWithError(e);
        }
    });
    return emitter;
}
```

- [ ] **Step 4: Run tests — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/config/StreamingConfig.java \
       backend-java/src/main/resources/application.yml
git commit -m "feat: add SSE streaming endpoint /rag_answer/stream"
```

---

## Chunk 4: Suggested Questions + Confidence + Citation Optimization

### Task 11: Prompt Template + JSON Output Parsing

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/application/rag/CitationAssemblyService.java` (lines 23-30 prompt template, add parseLlmOutput method)
- Create: `backend-java/src/main/java/com/huatai/rag/application/rag/ParsedLlmOutput.java`
- Test: `backend-java/src/test/java/com/huatai/rag/application/CitationAssemblyServiceBatchBTest.java`

- [ ] **Step 1: Write test for JSON parsing**

```java
class CitationAssemblyServiceBatchBTest {

    @Test
    void parseLlmOutput_extracts_answer_and_questions_from_json() {
        var service = new CitationAssemblyService();
        String json = """
                {"answer": "根据[1]，KYC审查流程...", "suggested_questions": ["q1?", "q2?"]}""";
        var parsed = service.parseLlmOutput(json);
        assertThat(parsed.answerText()).isEqualTo("根据[1]，KYC审查流程...");
        assertThat(parsed.suggestedQuestions()).containsExactly("q1?", "q2?");
    }

    @Test
    void parseLlmOutput_falls_back_on_invalid_json() {
        var service = new CitationAssemblyService();
        var parsed = service.parseLlmOutput("plain text answer without json");
        assertThat(parsed.answerText()).isEqualTo("plain text answer without json");
        assertThat(parsed.suggestedQuestions()).isEmpty();
    }

    @Test
    void parseLlmOutput_handles_json_wrapped_in_markdown_codeblock() {
        var service = new CitationAssemblyService();
        String wrapped = "```json\n{\"answer\": \"text\", \"suggested_questions\": [\"q\"]}\n```";
        var parsed = service.parseLlmOutput(wrapped);
        assertThat(parsed.answerText()).isEqualTo("text");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Create ParsedLlmOutput and implement parseLlmOutput()**

```java
// ParsedLlmOutput.java
package com.huatai.rag.application.rag;
import java.util.List;
public record ParsedLlmOutput(String answerText, List<String> suggestedQuestions) {}
```

In `CitationAssemblyService`, add `parseLlmOutput(String rawOutput)`:
- Try JSON parse with Jackson ObjectMapper
- Extract `answer` and `suggested_questions` fields
- If parse fails, return `new ParsedLlmOutput(rawOutput, List.of())`
- Handle markdown codeblock wrapping (strip ```json...```)

- [ ] **Step 4: Update prompt template** — add JSON output instruction + few-shot example per spec Section 6.1

- [ ] **Step 5: Run tests — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/rag/ \
       backend-java/src/test/java/com/huatai/rag/application/CitationAssemblyServiceBatchBTest.java
git commit -m "feat: add JSON output parsing for suggested questions"
```

---

### Task 12: Citation Display Optimization

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/application/rag/CitationAssemblyService.java` — inject DocumentRegistryPort, add noise filter + truncation + filename fallback

**Important:** This task adds `DocumentRegistryPort` as a constructor dependency to `CitationAssemblyService`, which is currently a zero-dependency class. This will require updating:
- `ApplicationWiringConfig` where it is instantiated
- Task 11's test (`new CitationAssemblyService()` → `new CitationAssemblyService(mockDocRegistryPort)`)
- Any other test that directly constructs `CitationAssemblyService`

- [ ] **Step 1: Write test for noise filtering and filename fallback**

```java
@Test
void cleanExcerpt_removes_ocr_noise() {
    var service = new CitationAssemblyService();
    String noisy = "HUATAI FINANCIAL ДЛ НІТ ** <<<<<< text content";
    String cleaned = service.cleanExcerpt(noisy);
    assertThat(cleaned).doesNotContain("ДЛ").doesNotContain("<<<<<<");
    assertThat(cleaned).contains("HUATAI FINANCIAL").contains("text content");
}

@Test
void cleanExcerpt_truncates_at_150_chars() {
    var service = new CitationAssemblyService();
    String longText = "A".repeat(200);
    String cleaned = service.cleanExcerpt(longText);
    assertThat(cleaned).hasSize(153); // 150 + "..."
    assertThat(cleaned).endsWith("...");
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement cleanExcerpt() and filename fallback**

```java
public String cleanExcerpt(String text) {
    text = text.replaceAll("[<>*#=]{3,}", "");
    text = text.replaceAll("[\\p{IsCyrillic}]+", "");
    text = text.replaceAll("\\s{2,}", " ").trim();
    if (text.length() > 150) {
        text = text.substring(0, 150) + "...";
    }
    return text;
}
```

Inject `DocumentRegistryPort` into `CitationAssemblyService` constructor. In `assemble()`, use:
```java
String filename = extractFromMetadata(metadata, "filename");
if (filename == null && indexName != null) {
    filename = documentRegistryPort.findByIndexName(indexName)
            .map(DocumentFileRecord::filename)
            .orElse(indexName);
}
```

- [ ] **Step 4: Run tests — expect PASS**
- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/rag/CitationAssemblyService.java \
       backend-java/src/test/
git commit -m "feat: add citation noise filtering, truncation, and filename fallback"
```

---

## Chunk 5: Pipeline Integration + Response Changes

### Task 13: RagRequest/RagResponse Changes

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagRequest.java` (remove @NotBlank on sessionId)
- Modify: `backend-java/src/main/java/com/huatai/rag/api/rag/dto/RagResponse.java` (add 4 new fields)

- [ ] **Step 1: Remove @NotBlank from RagRequest.sessionId** (line 11)

```java
// Before:
@JsonProperty("session_id")
@NotBlank
private String sessionId;

// After:
@JsonProperty("session_id")
private String sessionId;  // nullable for backward compat
```

- [ ] **Step 2: Add new fields to RagResponse**

```java
@JsonProperty("suggested_questions")
private List<String> suggestedQuestions = new ArrayList<>();

private String confidence;

@JsonProperty("history_compressed")
private boolean historyCompressed;

@JsonProperty("session_id")
private String sessionId;

// + getters and setters
```

- [ ] **Step 3: Run tests — expect PASS** (additive changes, backward compatible)
- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/api/rag/dto/
git commit -m "feat: update RagRequest/RagResponse for Batch B fields"
```

---

### Task 14: Pipeline Integration — handle() + QueryResult

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java` (lines 49-62 QueryResult, lines 64-199 Default)
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java` (lines 207-225)
- Modify: `backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java` (lines 30-54)
- Modify: `backend-java/src/test/java/com/huatai/rag/application/RagQueryApplicationServiceTest.java`

- [ ] **Step 1: Extend QueryResult record** (add `suggestedQuestions`, `confidence`, `historyCompressed`, `sessionId`)

```java
record QueryResult(
        String answer,
        List<RetrievedDocument> sourceDocuments,
        List<RetrievedDocument> recallDocuments,
        List<RetrievedDocument> rerankDocuments,
        List<Citation> citations,
        List<String> suggestedQuestions,     // NEW
        ConfidenceLevel confidence,          // NEW
        boolean historyCompressed,           // NEW
        UUID sessionId                       // NEW
) {
    QueryResult {
        sourceDocuments = List.copyOf(sourceDocuments);
        recallDocuments = List.copyOf(recallDocuments);
        rerankDocuments = List.copyOf(rerankDocuments);
        citations = List.copyOf(citations);
        suggestedQuestions = List.copyOf(suggestedQuestions);
    }
}
```

- [ ] **Step 2: Update Default.handle() to integrate all Batch B features**

Key changes in `handle()`:
1. Parse sessionId from command, auto-create session if null/empty
2. Load conversation context via `conversationMemoryService.loadContext(sessionId)`
3. Inject history into prompt context (in `CitationAssemblyService.assemble()`)
4. After LLM response: `parseLlmOutput()` → `parseResponse()` (new call sequence)
5. Compute `ConfidenceLevel.fromScore(maxRerankScore)`
6. Save USER + ASSISTANT messages via `chatMessagePort`
7. Return expanded QueryResult

- [ ] **Step 3: Update Default constructor** to accept new dependencies: `ConversationMemoryService`, `ChatSessionPort`, `ChatMessagePort`

- [ ] **Step 4: Update ApplicationWiringConfig** to wire new beans

- [ ] **Step 5: Update RagController** to map new QueryResult fields to RagResponse

- [ ] **Step 6: Fix all test compilation errors**

Files that construct `QueryResult` (grep for `new QueryResult(`):
- `RagQueryApplicationService.java` lines ~145 and ~185 (two return sites in `handle()`)
- `RagQueryApplicationServiceTest.java` — `FakeRagQueryDependencies` lambda
- `RagControllerContractTest.java` — any mock returning QueryResult
- `RagRegressionTest.java` — if it constructs QueryResult directly

All must be updated from 5-arg to 9-arg constructor (add `suggestedQuestions`, `confidence`, `historyCompressed`, `sessionId`).

- [ ] **Step 7: Run full test suite**

Run: `mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/rag/RagQueryApplicationService.java \
       backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java \
       backend-java/src/main/java/com/huatai/rag/api/rag/RagController.java \
       backend-java/src/test/
git commit -m "feat: integrate conversation memory, confidence, and suggested questions into pipeline"
```

---

## Chunk 6: User Feedback + Final Verification

### Task 15: Feedback API

**Files:**
- Create: `backend-java/src/main/java/com/huatai/rag/application/chat/FeedbackApplicationService.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/api/chat/ChatSessionController.java` (add feedback endpoints)
- Modify: `backend-java/src/main/java/com/huatai/rag/api/admin/AdminController.java` (add feedback admin endpoints)
- Test: `backend-java/src/test/java/com/huatai/rag/api/FeedbackContractTest.java`

- [ ] **Step 1: Create FeedbackApplicationService**

Methods: `submitFeedback(UUID sessionId, UUID messageId, String rating, String comment)`, `listFeedback(String ratingFilter, int page, int size)`, `getStats()`

- [ ] **Step 2: Add feedback endpoint to ChatSessionController**

```java
@PostMapping("/sessions/{sessionId}/messages/{messageId}/feedback")
public ResponseEntity<Void> submitFeedback(...) { ... }
```

- [ ] **Step 3: Add admin feedback endpoints to AdminController**

```java
@GetMapping("/admin/feedback")
public List<FeedbackDto> listFeedback(...) { ... }

@GetMapping("/admin/feedback/stats")
public FeedbackStats feedbackStats() { ... }
```

- [ ] **Step 4: Write contract test**

```java
@WebMvcTest
class FeedbackContractTest {
    @Test void submitFeedback_returns200() { ... }
    @Test void listFeedback_returns200() { ... }
    @Test void feedbackStats_returns200() { ... }
}
```

- [ ] **Step 5: Run tests — expect PASS**
- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/chat/FeedbackApplicationService.java \
       backend-java/src/main/java/com/huatai/rag/api/ \
       backend-java/src/test/java/com/huatai/rag/api/FeedbackContractTest.java
git commit -m "feat: add user feedback API (submit, list, stats)"
```

---

### Task 16: Frontend Minimal Integration (Feedback Buttons)

**Files:**
- Modify: `frontend/src/pages/QA.tsx` or NEWTON equivalent — add 👍👎 buttons

- [ ] **Step 1: Add feedback buttons below ASSISTANT messages**

Minimal change: after each answer display, render two icon buttons. On click, call `POST /sessions/{sid}/messages/{mid}/feedback`. Show highlighted state for already-rated messages.

- [ ] **Step 2: Manual test in browser**
- [ ] **Step 3: Commit**

```bash
git add frontend/src/
git commit -m "feat: add minimal feedback buttons to QA page"
```

---

### Task 17: Bean Wiring + Configuration Finalization

**Files:**
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java`
- Modify: `backend-java/src/main/resources/application.yml`

**Wiring split clarification:** Task 14 already wired pipeline dependencies (`ConversationMemoryService`, `ChatSessionPort`, `ChatMessagePort` into `Default` constructor). Task 17 wires the **remaining** beans not yet connected:

- [ ] **Step 1: Wire remaining beans in ApplicationWiringConfig**

```java
// Feedback
@Bean public ChatFeedbackPort chatFeedbackPort(...) { ... }
@Bean public FeedbackApplicationService feedbackApplicationService(...) { ... }

// Chat session application service (if not already wired in Task 8)
@Bean public ChatSessionApplicationService chatSessionApplicationService(...) { ... }
```

- [ ] **Step 2: Add conversation memory config to application.yml**

```yaml
huatai:
  conversation:
    window-size: ${CONVERSATION_WINDOW_SIZE:10}
    keep-recent: ${CONVERSATION_KEEP_RECENT:6}
```

- [ ] **Step 3: Run full test suite**
- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/config/ \
       backend-java/src/main/resources/application.yml
git commit -m "feat: wire Batch B beans and finalize configuration"
```

---

### Task 18: Full Verification

- [ ] **Step 1: Run complete test suite**

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
```
Expected: All tests PASS (86 existing + ~25-30 new)

- [ ] **Step 2: Verify application starts**

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
curl http://localhost:8001/health
```

- [ ] **Step 3: Test SSE endpoint** (if live Bedrock available)

```bash
curl -N -X POST http://localhost:8001/rag_answer/stream \
  -H "Content-Type: application/json" \
  -d '{"query":"test","index_names":["test"]}'
```

- [ ] **Step 4: Fix any remaining issues**

---

### Task 19: Update Control Documents

- [ ] **Step 1: Update control/enhanced_rag_b/Plan.md** — check all milestone items

- [ ] **Step 2: Update control/enhanced_rag_b/Documentation.md** — update status, test count

- [ ] **Step 3: Update CLAUDE.md** — reflect Batch B completion

- [ ] **Step 4: Commit**

```bash
git add control/enhanced_rag_b/ CLAUDE.md
git commit -m "docs: update control documents for Batch B completion"
```
