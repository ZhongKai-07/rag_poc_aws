package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.ChatMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAt(UUID sessionId);
    List<ChatMessageEntity> findTop10BySessionIdOrderByCreatedAtDesc(UUID sessionId);
    int countBySessionId(UUID sessionId);
}
