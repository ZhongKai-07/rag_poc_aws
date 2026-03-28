package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.infrastructure.persistence.entity.ChatSessionEntity;
import com.huatai.rag.infrastructure.persistence.entity.ChatMessageEntity;
import com.huatai.rag.infrastructure.persistence.entity.ChatFeedbackEntity;
import com.huatai.rag.infrastructure.persistence.repository.ChatSessionJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.ChatMessageJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.ChatFeedbackJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ChatPersistenceTest {

    @Autowired
    ChatSessionJpaRepository sessionRepo;

    @Autowired
    ChatMessageJpaRepository messageRepo;

    @Autowired
    ChatFeedbackJpaRepository feedbackRepo;

    @Test
    void save_and_load_session() {
        var entity = new ChatSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setTitle("Test Session");
        entity.setModule("RAG");
        sessionRepo.save(entity);

        var found = sessionRepo.findById(entity.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Session");
    }

    @Test
    void save_and_load_messages() {
        var session = new ChatSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTitle("Session");
        session.setModule("RAG");
        sessionRepo.save(session);

        var msg = new ChatMessageEntity();
        msg.setId(UUID.randomUUID());
        msg.setSessionId(session.getId());
        msg.setRole("USER");
        msg.setContent("hello");
        messageRepo.save(msg);

        var messages = messageRepo.findBySessionIdOrderByCreatedAt(session.getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("hello");
    }

    @Test
    void count_messages_by_session() {
        var session = new ChatSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTitle("Session");
        session.setModule("RAG");
        sessionRepo.save(session);

        for (int i = 0; i < 3; i++) {
            var msg = new ChatMessageEntity();
            msg.setId(UUID.randomUUID());
            msg.setSessionId(session.getId());
            msg.setRole(i % 2 == 0 ? "USER" : "ASSISTANT");
            msg.setContent("msg " + i);
            messageRepo.save(msg);
        }

        assertThat(messageRepo.countBySessionId(session.getId())).isEqualTo(3);
    }

    @Test
    void save_and_load_feedback() {
        var session = new ChatSessionEntity();
        session.setId(UUID.randomUUID());
        session.setTitle("Session");
        session.setModule("RAG");
        sessionRepo.save(session);

        var msg = new ChatMessageEntity();
        msg.setId(UUID.randomUUID());
        msg.setSessionId(session.getId());
        msg.setRole("ASSISTANT");
        msg.setContent("answer");
        messageRepo.save(msg);

        var fb = new ChatFeedbackEntity();
        fb.setId(UUID.randomUUID());
        fb.setMessageId(msg.getId());
        fb.setSessionId(session.getId());
        fb.setRating("THUMBS_UP");
        feedbackRepo.save(fb);

        var found = feedbackRepo.findByMessageId(msg.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRating()).isEqualTo("THUMBS_UP");
    }
}
