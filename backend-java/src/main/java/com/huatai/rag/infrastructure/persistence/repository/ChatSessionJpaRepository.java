package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.ChatSessionEntity;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
