package com.huatai.rag.regression;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.domain.document.DocumentFileRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.document.IndexNamingPolicy;
import com.huatai.rag.domain.document.IngestionJobRecord;
import com.huatai.rag.domain.document.IngestionStatus;
import com.huatai.rag.domain.parser.DocumentParser;
import com.huatai.rag.domain.parser.ParsedChunk;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParsedPage;
import com.huatai.rag.domain.parser.ParserRequest;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionRegressionTest {

    private static final String QUESTIONS_FIXTURE = "fixtures/regression/questions.csv";

    @Test
    void baselineQuestionFixtureKeepsPythonCompatibleIndexNames() throws IOException {
        Map<String, String> expectedPairs = readFixtureIndexPairs(QUESTIONS_FIXTURE);

        assertThat(expectedPairs)
                .allSatisfy((filename, expectedIndexName) ->
                        assertThat(IndexNamingPolicy.indexNameFor(filename)).isEqualTo(expectedIndexName));
    }

    @Test
    void completedFilesAreNotReprocessedWhenUploadedAgain() {
        DocumentFileRecord existingRecord = new DocumentFileRecord(
                UUID.randomUUID(),
                "PRC Client.pdf",
                "2374dcf7",
                "C:/baseline/PRC Client.pdf",
                IngestionStatus.COMPLETED,
                Instant.parse("2026-03-19T00:00:00Z"),
                Instant.parse("2026-03-19T00:00:00Z"));
        FakeDocumentRegistryPort registryPort = new FakeDocumentRegistryPort(existingRecord);
        CountingStorage storage = new CountingStorage();
        CountingParser parser = new CountingParser();
        CountingEmbeddingPort embeddingPort = new CountingEmbeddingPort();
        CountingChunkWriter chunkWriter = new CountingChunkWriter();

        DocumentIngestionApplicationService.Default service = new DocumentIngestionApplicationService.Default(
                storage,
                registryPort,
                parser,
                embeddingPort,
                chunkWriter);

        DocumentIngestionApplicationService.IngestionResult result = service.handle(
                new DocumentIngestionApplicationService.IngestionCommand(
                        "PRC Client.pdf",
                        "pdf".getBytes(StandardCharsets.UTF_8),
                        "./documents/test-batch"));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Files processed successfully");
        assertThat(result.indexName()).isEqualTo("2374dcf7");
        assertThat(result.storagePath()).isEqualTo("C:/baseline/PRC Client.pdf");
        assertThat(storage.invocationCount).isZero();
        assertThat(parser.invocationCount).isZero();
        assertThat(embeddingPort.invocationCount).isZero();
        assertThat(chunkWriter.ensureIndexInvocationCount).isZero();
        assertThat(chunkWriter.writeInvocationCount).isZero();
        assertThat(registryPort.savedDocuments).isEmpty();
        assertThat(registryPort.savedJobs).isEmpty();
    }

    private static Map<String, String> readFixtureIndexPairs(String resourcePath) throws IOException {
        Map<String, String> pairs = new LinkedHashMap<>();
        try (InputStream inputStream = resourcePath(resourcePath)) {
            for (String line : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .skip(1)
                    .filter(value -> !value.isBlank())
                    .toList()) {
                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (!columns[0].contains("|")) {
                    pairs.put(columns[0], columns[1]);
                }
            }
        }
        return pairs;
    }

    private static InputStream resourcePath(String resourcePath) {
        InputStream inputStream = IngestionRegressionTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Missing regression fixture: " + resourcePath);
        }
        return inputStream;
    }

    private static final class FakeDocumentRegistryPort implements DocumentRegistryPort {
        private final DocumentFileRecord existingRecord;
        private final List<DocumentFileRecord> savedDocuments = new ArrayList<>();
        private final List<IngestionJobRecord> savedJobs = new ArrayList<>();

        private FakeDocumentRegistryPort(DocumentFileRecord existingRecord) {
            this.existingRecord = existingRecord;
        }

        @Override
        public DocumentFileRecord saveDocument(DocumentFileRecord documentFileRecord) {
            savedDocuments.add(documentFileRecord);
            return documentFileRecord;
        }

        @Override
        public IngestionJobRecord saveIngestionJob(IngestionJobRecord ingestionJobRecord) {
            savedJobs.add(ingestionJobRecord);
            return ingestionJobRecord;
        }

        @Override
        public Optional<DocumentFileRecord> findByFilename(String filename) {
            if (existingRecord.filename().equals(filename)) {
                return Optional.of(existingRecord);
            }
            return Optional.empty();
        }

        @Override
        public Optional<DocumentFileRecord> findByIndexName(String indexName) {
            if (existingRecord.indexName().equals(indexName)) {
                return Optional.of(existingRecord);
            }
            return Optional.empty();
        }

        @Override
        public List<DocumentFileRecord> listProcessedFiles() {
            return List.of(existingRecord);
        }
    }

    private static final class CountingStorage implements DocumentIngestionApplicationService.DocumentStorage {
        private int invocationCount;

        @Override
        public DocumentIngestionApplicationService.StoredDocument store(String filename, byte[] content, String directoryPath) {
            invocationCount++;
            return new DocumentIngestionApplicationService.StoredDocument(filename, "C:/new/" + filename);
        }
    }

    private static final class CountingParser implements DocumentParser {
        private int invocationCount;

        @Override
        public ParsedDocument parse(ParserRequest request) {
            invocationCount++;
            ParsedChunk chunk = new ParsedChunk(
                    "chunk-1",
                    1,
                    "PRC client onboarding content.",
                    "PRC client onboarding content.",
                    List.of("Client"),
                    List.of(),
                    Map.of("source", request.fileName()));
            return new ParsedDocument(
                    request.fileName(),
                    request.indexName(),
                    List.of(new ParsedPage(1, "Client", List.of("Client"))),
                    List.of(chunk),
                    List.of(),
                    "",
                    "aws-bda",
                    "2025-03-01");
        }
    }

    private static final class CountingEmbeddingPort implements EmbeddingPort {
        private int invocationCount;

        @Override
        public List<List<Float>> embedAll(List<String> texts) {
            invocationCount++;
            return List.of(List.of(0.1f, 0.2f, 0.3f));
        }
    }

    private static final class CountingChunkWriter implements DocumentIngestionApplicationService.DocumentChunkWriter {
        private int ensureIndexInvocationCount;
        private int writeInvocationCount;

        @Override
        public void ensureIndexExists(String indexName, int embeddingDimension) {
            ensureIndexInvocationCount++;
        }

        @Override
        public void writeChunks(String indexName, List<ParsedChunk> chunks, List<List<Float>> embeddings) {
            writeInvocationCount++;
        }
    }
}
