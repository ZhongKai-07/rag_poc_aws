package com.huatai.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.application.registry.ProcessedFileQueryApplicationService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentIngestionApplicationServiceTest {

    @Test
    void ingestionServiceStoresParsesEmbedsIndexesAndMarksDocumentCompleted() {
        FakeDocumentRegistryPort registryPort = new FakeDocumentRegistryPort();
        FakeStorage storage = new FakeStorage("s3://huatai-rag/input/PRC Client.pdf");
        FakeIndexWriter indexWriter = new FakeIndexWriter();
        ParsedChunk chunk = new ParsedChunk(
                "chunk-1",
                1,
                "The client agreement sets out onboarding steps and required approvals.",
                "The client agreement sets out onboarding steps.",
                List.of("Executive Summary"),
                List.of(),
                Map.of("source", "PRC Client.pdf", "chunk_id", "chunk-1"));
        ParsedDocument parsedDocument = new ParsedDocument(
                "PRC Client.pdf",
                IndexNamingPolicy.indexNameFor("PRC Client.pdf"),
                List.of(new ParsedPage(1, "Executive Summary", List.of("Executive Summary"))),
                List.of(chunk),
                List.of(),
                "s3://huatai-rag/_bda_output/2374dcf7.json",  // s3OutputPath
                "aws-bda",                                      // parserType
                "2025-03-01");                                  // parserVersion
        FakeParser parser = new FakeParser(parsedDocument);
        FakeEmbeddingPort embeddingPort = new FakeEmbeddingPort(List.of(List.of(0.1f, 0.2f, 0.3f)));

        DocumentIngestionApplicationService.Default service = new DocumentIngestionApplicationService.Default(
                storage,
                registryPort,
                parser,
                embeddingPort,
                indexWriter);

        DocumentIngestionApplicationService.IngestionResult result = service.handle(
                new DocumentIngestionApplicationService.IngestionCommand(
                        "PRC Client.pdf",
                        "pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "./documents/test-batch"));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Files processed successfully");
        assertThat(result.indexName()).isEqualTo("2374dcf7");
        assertThat(storage.savedDirectoryPath).isEqualTo("./documents/test-batch");
        assertThat(parser.lastRequest).isEqualTo(new ParserRequest("PRC Client.pdf", "2374dcf7", "s3://huatai-rag/input/PRC Client.pdf"));
        assertThat(embeddingPort.embeddedTexts).containsExactly("The client agreement sets out onboarding steps.");
        assertThat(indexWriter.ensuredIndexName).isEqualTo("2374dcf7");
        assertThat(indexWriter.ensuredVectorDimension).isEqualTo(3);
        assertThat(indexWriter.writtenChunks).containsExactly(chunk);
        assertThat(registryPort.documentStatuses)
                .extracting(DocumentFileRecord::status)
                .containsExactly(IngestionStatus.PROCESSING, IngestionStatus.COMPLETED);
        assertThat(registryPort.ingestionStatuses)
                .extracting(IngestionJobRecord::status)
                .containsExactly(IngestionStatus.PROCESSING, IngestionStatus.COMPLETED);
    }

    @Test
    void processedFileQueryServiceMapsRegistryResultsAndMissingFileLookup() {
        FakeDocumentRegistryPort registryPort = new FakeDocumentRegistryPort();
        registryPort.listedFiles = List.of(
                new DocumentFileRecord(
                        UUID.randomUUID(),
                        "PRC Client.pdf",
                        "2374dcf7",
                        "s3://huatai-rag/input/PRC Client.pdf",
                        IngestionStatus.COMPLETED,
                        Instant.parse("2026-03-19T00:00:00Z"),
                        Instant.parse("2026-03-19T00:00:00Z")));
        ProcessedFileQueryApplicationService.Default service = new ProcessedFileQueryApplicationService.Default(registryPort);

        ProcessedFileQueryApplicationService.ProcessedFilesResult processedFiles = service.listProcessedFilesView();
        ProcessedFileQueryApplicationService.IndexLookupResult found = service.findIndexByFilename("PRC Client.pdf");
        ProcessedFileQueryApplicationService.IndexLookupResult missing = service.findIndexByFilename("missing.pdf");

        assertThat(processedFiles.status()).isEqualTo("success");
        assertThat(processedFiles.files())
                .extracting(ProcessedFileQueryApplicationService.ProcessedFileResult::indexName)
                .containsExactly("2374dcf7");
        assertThat(found.status()).isEqualTo("success");
        assertThat(found.indexName()).isEqualTo("2374dcf7");
        assertThat(missing.status()).isEqualTo("error");
        assertThat(missing.message()).isEqualTo("File not found");
    }

    private static final class FakeDocumentRegistryPort implements DocumentRegistryPort {
        private final List<DocumentFileRecord> documentStatuses = new ArrayList<>();
        private final List<IngestionJobRecord> ingestionStatuses = new ArrayList<>();
        private List<DocumentFileRecord> listedFiles = List.of();

        @Override
        public DocumentFileRecord saveDocument(DocumentFileRecord documentFileRecord) {
            DocumentFileRecord savedRecord = new DocumentFileRecord(
                    documentFileRecord.id() == null ? UUID.randomUUID() : documentFileRecord.id(),
                    documentFileRecord.filename(),
                    documentFileRecord.indexName(),
                    documentFileRecord.storagePath(),
                    documentFileRecord.status(),
                    documentFileRecord.createdAt() == null ? Instant.parse("2026-03-19T00:00:00Z") : documentFileRecord.createdAt(),
                    Instant.parse("2026-03-19T00:00:00Z"));
            documentStatuses.add(savedRecord);
            listedFiles = List.of(savedRecord);
            return savedRecord;
        }

        @Override
        public IngestionJobRecord saveIngestionJob(IngestionJobRecord ingestionJobRecord) {
            IngestionJobRecord savedRecord = new IngestionJobRecord(
                    ingestionJobRecord.id() == null ? UUID.randomUUID() : ingestionJobRecord.id(),
                    ingestionJobRecord.documentFileId(),
                    ingestionJobRecord.indexName(),
                    ingestionJobRecord.status(),
                    ingestionJobRecord.createdAt() == null ? Instant.parse("2026-03-19T00:00:00Z") : ingestionJobRecord.createdAt(),
                    Instant.parse("2026-03-19T00:00:00Z"));
            ingestionStatuses.add(savedRecord);
            return savedRecord;
        }

        @Override
        public Optional<DocumentFileRecord> findByFilename(String filename) {
            return listedFiles.stream().filter(record -> record.filename().equals(filename)).findFirst();
        }

        @Override
        public Optional<DocumentFileRecord> findByIndexName(String indexName) {
            return listedFiles.stream().filter(record -> record.indexName().equals(indexName)).findFirst();
        }

        @Override
        public List<DocumentFileRecord> listProcessedFiles() {
            return listedFiles;
        }
    }

    private static final class FakeStorage implements DocumentIngestionApplicationService.DocumentStorage {
        private final String storedPath;
        private String savedDirectoryPath;

        private FakeStorage(String storedPath) {
            this.storedPath = storedPath;
        }

        @Override
        public DocumentIngestionApplicationService.StoredDocument store(String filename, byte[] content, String directoryPath) {
            this.savedDirectoryPath = directoryPath;
            return new DocumentIngestionApplicationService.StoredDocument(filename, storedPath);
        }
    }

    private static final class FakeParser implements DocumentParser {
        private final ParsedDocument parsedDocument;
        private ParserRequest lastRequest;

        private FakeParser(ParsedDocument parsedDocument) {
            this.parsedDocument = parsedDocument;
        }

        @Override
        public ParsedDocument parse(ParserRequest request) {
            this.lastRequest = request;
            return parsedDocument;
        }
    }

    private static final class FakeEmbeddingPort implements EmbeddingPort {
        private final List<List<Float>> vectors;
        private List<String> embeddedTexts = List.of();

        private FakeEmbeddingPort(List<List<Float>> vectors) {
            this.vectors = vectors;
        }

        @Override
        public List<List<Float>> embedAll(List<String> texts) {
            this.embeddedTexts = texts;
            return vectors;
        }
    }

    private static final class FakeIndexWriter implements DocumentIngestionApplicationService.DocumentChunkWriter {
        private String ensuredIndexName;
        private int ensuredVectorDimension;
        private List<ParsedChunk> writtenChunks = List.of();

        @Override
        public void ensureIndexExists(String indexName, int embeddingDimension) {
            this.ensuredIndexName = indexName;
            this.ensuredVectorDimension = embeddingDimension;
        }

        @Override
        public void writeChunks(String indexName, List<ParsedChunk> chunks, List<List<Float>> embeddings) {
            this.writtenChunks = chunks;
        }
    }
}
