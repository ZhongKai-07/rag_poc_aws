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
        when(msgPort.countMessages(sessionId)).thenReturn(4);
        when(msgPort.loadAll(sessionId)).thenReturn(List.of(
                new ChatMessage(UUID.randomUUID(), sessionId, "USER", "q1", null, null, Instant.now()),
                new ChatMessage(UUID.randomUUID(), sessionId, "ASSISTANT", "a1", null, null, Instant.now()),
                new ChatMessage(UUID.randomUUID(), sessionId, "USER", "q2", null, null, Instant.now()),
                new ChatMessage(UUID.randomUUID(), sessionId, "ASSISTANT", "a2", null, null, Instant.now())
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
        when(msgPort.countMessages(sessionId)).thenReturn(12);
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
