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
