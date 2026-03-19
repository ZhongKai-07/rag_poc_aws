package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.IngestionJobEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobJpaRepository extends JpaRepository<IngestionJobEntity, UUID> {

    List<IngestionJobEntity> findByDocumentFileId(UUID documentFileId);
}
