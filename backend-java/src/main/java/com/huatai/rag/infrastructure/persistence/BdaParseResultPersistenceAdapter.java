package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class BdaParseResultPersistenceAdapter implements BdaParseResultPort {

    private final BdaParseResultJpaRepository jpaRepository;

    public BdaParseResultPersistenceAdapter(BdaParseResultJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository");
    }

    @Override
    public BdaParseResultRecord save(BdaParseResultRecord record) {
        BdaParseResultEntity entity = new BdaParseResultEntity();
        entity.setId(record.id() == null ? UUID.randomUUID() : record.id());
        entity.setDocumentFileId(record.documentFileId());
        entity.setIndexName(record.indexName());
        entity.setS3OutputPath(record.s3OutputPath());
        entity.setChunkCount(record.chunkCount());
        entity.setPageCount(record.pageCount());
        entity.setParserType(record.parserType());
        entity.setParserVersion(record.parserVersion());
        entity.setCreatedAt(record.createdAt());
        return toRecord(jpaRepository.save(entity));
    }

    @Override
    public List<BdaParseResultRecord> findAll() {
        return jpaRepository.findAll().stream().map(this::toRecord).toList();
    }

    @Override
    public Optional<BdaParseResultRecord> findLatestByIndexName(String indexName) {
        return jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc(indexName).map(this::toRecord);
    }

    private BdaParseResultRecord toRecord(BdaParseResultEntity entity) {
        return new BdaParseResultRecord(
                entity.getId(),
                entity.getDocumentFileId(),
                entity.getIndexName(),
                entity.getS3OutputPath(),
                entity.getChunkCount(),
                entity.getPageCount(),
                entity.getParserType(),
                entity.getParserVersion(),
                entity.getCreatedAt());
    }
}
