package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.domain.chat.ChatFeedback;
import com.huatai.rag.domain.chat.ChatFeedbackPort;
import com.huatai.rag.domain.chat.FeedbackStats;
import com.huatai.rag.infrastructure.persistence.entity.ChatFeedbackEntity;
import com.huatai.rag.infrastructure.persistence.repository.ChatFeedbackJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

public class ChatFeedbackPersistenceAdapter implements ChatFeedbackPort {

    private final ChatFeedbackJpaRepository repository;

    public ChatFeedbackPersistenceAdapter(ChatFeedbackJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsert(ChatFeedback feedback) {
        var existing = repository.findByMessageId(feedback.messageId());
        ChatFeedbackEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setRating(feedback.rating());
            entity.setComment(feedback.comment());
        } else {
            entity = new ChatFeedbackEntity();
            entity.setId(feedback.id());
            entity.setMessageId(feedback.messageId());
            entity.setSessionId(feedback.sessionId());
            entity.setRating(feedback.rating());
            entity.setComment(feedback.comment());
        }
        repository.save(entity);
    }

    @Override
    public Optional<ChatFeedback> findByMessageId(UUID messageId) {
        return repository.findByMessageId(messageId).map(this::toDomain);
    }

    @Override
    public List<ChatFeedback> list(String ratingFilter, int page, int size) {
        var pageable = PageRequest.of(page, size);
        List<ChatFeedbackEntity> entities;
        if (ratingFilter != null && !ratingFilter.isBlank()) {
            entities = repository.findByRatingOrderByCreatedAtDesc(ratingFilter, pageable);
        } else {
            entities = repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public FeedbackStats stats() {
        long total = repository.count();
        long thumbsUp = repository.countByRating("THUMBS_UP");
        long thumbsDown = repository.countByRating("THUMBS_DOWN");
        double rate = total > 0 ? (double) thumbsUp / total : 0.0;
        return new FeedbackStats(total, thumbsUp, thumbsDown, rate);
    }

    private ChatFeedback toDomain(ChatFeedbackEntity entity) {
        return new ChatFeedback(entity.getId(), entity.getMessageId(), entity.getSessionId(),
                entity.getRating(), entity.getComment(), entity.getCreatedAt());
    }
}
