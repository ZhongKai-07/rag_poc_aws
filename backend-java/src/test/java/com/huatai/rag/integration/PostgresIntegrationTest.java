package com.huatai.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.infrastructure.persistence.entity.DocumentFileEntity;
import com.huatai.rag.infrastructure.persistence.entity.IngestionJobEntity;
import com.huatai.rag.infrastructure.persistence.entity.QuestionHistoryEntity;
import com.huatai.rag.infrastructure.persistence.repository.DocumentFileJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.IngestionJobJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.QuestionHistoryJpaRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PostgresIntegrationTest {

    @MockBean
    private RestClient restClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentFileJpaRepository documentFileJpaRepository;

    @Autowired
    private IngestionJobJpaRepository ingestionJobJpaRepository;

    @Autowired
    private QuestionHistoryJpaRepository questionHistoryJpaRepository;

    @Test
    void flywayCreatesRequiredTablesAndRepositoriesCanPersistRecords() {
        Set<String> tableNames = Set.copyOf(
                jdbcTemplate.queryForList(
                                "select table_name from information_schema.tables where table_schema = 'PUBLIC'",
                                String.class)
                        .stream()
                        .map(String::toLowerCase)
                        .toList());

        assertThat(tableNames).contains("document_file", "ingestion_job", "question_history");

        DocumentFileEntity documentFile = new DocumentFileEntity();
        documentFile.setId(UUID.randomUUID());
        documentFile.setFilename("baseline.pdf");
        documentFile.setIndexName("abcd1234");
        documentFile.setStoragePath("documents/baseline.pdf");
        documentFile.setStatus("COMPLETED");
        documentFile.setCreatedAt(Instant.now());
        documentFile.setUpdatedAt(Instant.now());
        documentFileJpaRepository.saveAndFlush(documentFile);

        IngestionJobEntity ingestionJob = new IngestionJobEntity();
        ingestionJob.setId(UUID.randomUUID());
        ingestionJob.setDocumentFileId(documentFile.getId());
        ingestionJob.setIndexName(documentFile.getIndexName());
        ingestionJob.setStatus("COMPLETED");
        ingestionJob.setCreatedAt(Instant.now());
        ingestionJob.setUpdatedAt(Instant.now());
        ingestionJobJpaRepository.saveAndFlush(ingestionJob);

        QuestionHistoryEntity questionHistory = new QuestionHistoryEntity();
        questionHistory.setId(UUID.randomUUID());
        questionHistory.setIndexName(documentFile.getIndexName());
        questionHistory.setQuestion("What is the baseline requirement?");
        questionHistory.setAskedAt(Instant.now());
        questionHistoryJpaRepository.saveAndFlush(questionHistory);

        assertThat(documentFileJpaRepository.findByIndexName(documentFile.getIndexName()))
                .isPresent()
                .get()
                .extracting(DocumentFileEntity::getFilename)
                .isEqualTo("baseline.pdf");

        assertThat(ingestionJobJpaRepository.findByDocumentFileId(documentFile.getId()))
                .hasSize(1);

        assertThat(questionHistoryJpaRepository.findTop5ByIndexNameGroupByQuestionOrderByCountDesc(documentFile.getIndexName()))
                .isNotEmpty();
    }
}
