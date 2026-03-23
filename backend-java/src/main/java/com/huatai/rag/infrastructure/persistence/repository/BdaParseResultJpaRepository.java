package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BdaParseResultJpaRepository extends JpaRepository<BdaParseResultEntity, UUID> {

    Optional<BdaParseResultEntity> findFirstByIndexNameOrderByCreatedAtDesc(String indexName);
}
