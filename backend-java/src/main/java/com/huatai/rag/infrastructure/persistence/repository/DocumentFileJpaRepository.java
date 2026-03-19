package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.DocumentFileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFileJpaRepository extends JpaRepository<DocumentFileEntity, UUID> {

    Optional<DocumentFileEntity> findByIndexName(String indexName);

    Optional<DocumentFileEntity> findByFilename(String filename);
}
