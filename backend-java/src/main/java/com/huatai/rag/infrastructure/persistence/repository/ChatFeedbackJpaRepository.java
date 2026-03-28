package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.ChatFeedbackEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ChatFeedbackJpaRepository extends JpaRepository<ChatFeedbackEntity, UUID> {
    Optional<ChatFeedbackEntity> findByMessageId(UUID messageId);

    List<ChatFeedbackEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ChatFeedbackEntity> findByRatingOrderByCreatedAtDesc(String rating, Pageable pageable);

    long countByRating(String rating);
}
