package com.huatai.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;
import com.huatai.rag.infrastructure.persistence.entity.DocumentFileEntity;
import com.huatai.rag.infrastructure.persistence.repository.DocumentFileJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class BdaParseResultRepositoryTest {

    @Autowired
    private BdaParseResultJpaRepository jpaRepository;

    @Autowired
    private DocumentFileJpaRepository documentFileRepository;

    /** V4 migration has `document_file_id REFERENCES document_file(id)`.
     *  Insert a parent row first to satisfy the FK constraint. */
    private UUID insertDocumentFile(String filename, String indexName) {
        var docFile = new DocumentFileEntity();
        UUID id = UUID.randomUUID();
        docFile.setId(id);
        docFile.setFilename(filename);
        docFile.setIndexName(indexName);
        docFile.setStoragePath("s3://bucket/" + filename);
        docFile.setStatus("PROCESSED");
        documentFileRepository.save(docFile);
        return id;
    }

    @Test
    void savesAndFindsParseResult() {
        UUID docId = insertDocumentFile("sample.pdf", "ced4c5ef");
        var entity = new BdaParseResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setDocumentFileId(docId);
        entity.setIndexName("ced4c5ef");
        entity.setS3OutputPath("s3://bucket/_bda_output/ced4c5ef.json/job-1/result.json");
        entity.setChunkCount(29);
        entity.setPageCount(8);
        entity.setParserType("aws-bda");
        entity.setParserVersion("2025-03-01");
        entity.setCreatedAt(Instant.now());

        jpaRepository.save(entity);

        List<BdaParseResultEntity> all = jpaRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getIndexName()).isEqualTo("ced4c5ef");
    }

    @Test
    void findsLatestByIndexName() {
        UUID docId = insertDocumentFile("report.pdf", "ced4c5ef");
        Instant older = Instant.parse("2026-03-01T00:00:00Z");
        Instant newer = Instant.parse("2026-03-22T00:00:00Z");

        for (Instant ts : List.of(older, newer)) {
            var e = new BdaParseResultEntity();
            e.setId(UUID.randomUUID());
            e.setDocumentFileId(docId);
            e.setIndexName("ced4c5ef");
            e.setS3OutputPath("s3://bucket/result.json");
            e.setChunkCount(10);
            e.setPageCount(3);
            e.setParserType("aws-bda");
            e.setParserVersion("2025-03-01");
            e.setCreatedAt(ts);
            jpaRepository.save(e);
        }

        Optional<BdaParseResultEntity> latest =
                jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc("ced4c5ef");

        assertThat(latest).isPresent();
        assertThat(latest.get().getCreatedAt()).isEqualTo(newer);
    }
}
