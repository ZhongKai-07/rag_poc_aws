package com.huatai.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BdaParseResultPersistenceAdapterTest {

    private final BdaParseResultJpaRepository jpaRepository = mock(BdaParseResultJpaRepository.class);
    private final BdaParseResultPersistenceAdapter adapter = new BdaParseResultPersistenceAdapter(jpaRepository);

    @Test
    void saveMapsRecordToEntityAndBack() {
        UUID id = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Instant now = Instant.now();
        BdaParseResultRecord record = new BdaParseResultRecord(
                id, docId, "ced4c5ef", "s3://bucket/result.json",
                29, 8, "aws-bda", "2025-03-01", now);

        BdaParseResultEntity savedEntity = new BdaParseResultEntity();
        savedEntity.setId(id);
        savedEntity.setDocumentFileId(docId);
        savedEntity.setIndexName("ced4c5ef");
        savedEntity.setS3OutputPath("s3://bucket/result.json");
        savedEntity.setChunkCount(29);
        savedEntity.setPageCount(8);
        savedEntity.setParserType("aws-bda");
        savedEntity.setParserVersion("2025-03-01");
        savedEntity.setCreatedAt(now);
        when(jpaRepository.save(any())).thenReturn(savedEntity);

        BdaParseResultRecord result = adapter.save(record);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.indexName()).isEqualTo("ced4c5ef");
        assertThat(result.chunkCount()).isEqualTo(29);
        assertThat(result.parserType()).isEqualTo("aws-bda");
    }

    @Test
    void findLatestByIndexNameMapsOptionalEntityToOptionalRecord() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        BdaParseResultEntity entity = new BdaParseResultEntity();
        entity.setId(id);
        entity.setDocumentFileId(UUID.randomUUID());
        entity.setIndexName("ced4c5ef");
        entity.setS3OutputPath("s3://bucket/result.json");
        entity.setChunkCount(5);
        entity.setPageCount(2);
        entity.setParserType("aws-bda");
        entity.setParserVersion("2025-03-01");
        entity.setCreatedAt(now);
        when(jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc("ced4c5ef"))
                .thenReturn(Optional.of(entity));

        Optional<BdaParseResultRecord> result = adapter.findLatestByIndexName("ced4c5ef");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().pageCount()).isEqualTo(2);
    }

    @Test
    void findAllDelegatesToJpaAndMapsToRecords() {
        BdaParseResultEntity entity = new BdaParseResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setDocumentFileId(UUID.randomUUID());
        entity.setIndexName("abc12345");
        entity.setS3OutputPath("s3://bucket/result.json");
        entity.setChunkCount(3);
        entity.setPageCount(1);
        entity.setParserType("aws-bda");
        entity.setParserVersion("2025-03-01");
        entity.setCreatedAt(Instant.now());
        when(jpaRepository.findAll()).thenReturn(List.of(entity));

        List<BdaParseResultRecord> records = adapter.findAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).indexName()).isEqualTo("abc12345");
    }

    @Test
    void findLatestByIndexNameReturnsEmptyWhenNotFound() {
        when(jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc("nonexistent"))
                .thenReturn(Optional.empty());

        assertThat(adapter.findLatestByIndexName("nonexistent")).isEmpty();
    }
}
