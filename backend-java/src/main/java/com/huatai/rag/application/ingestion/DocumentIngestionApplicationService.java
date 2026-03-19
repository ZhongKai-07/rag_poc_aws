package com.huatai.rag.application.ingestion;

import com.huatai.rag.domain.document.DocumentFileRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.document.IndexNamingPolicy;
import com.huatai.rag.domain.document.IngestionJobRecord;
import com.huatai.rag.domain.document.IngestionStatus;
import com.huatai.rag.domain.parser.DocumentParser;
import com.huatai.rag.domain.parser.ParsedChunk;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParserRequest;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface DocumentIngestionApplicationService {

    IngestionResult handle(IngestionCommand command);

    record IngestionCommand(String filename, byte[] content, String directoryPath) {

        public IngestionCommand {
            content = content == null ? new byte[0] : content.clone();
        }
    }

    record IngestionResult(String status, String message, String indexName, String storagePath) {
    }

    record StoredDocument(String filename, String storagePath) {
    }

    interface DocumentStorage {
        StoredDocument store(String filename, byte[] content, String directoryPath);
    }

    interface DocumentChunkWriter {
        void ensureIndexExists(String indexName, int embeddingDimension);

        void writeChunks(String indexName, List<ParsedChunk> chunks, List<List<Float>> embeddings);
    }

    final class Default implements DocumentIngestionApplicationService {
        private final DocumentStorage documentStorage;
        private final DocumentRegistryPort documentRegistryPort;
        private final DocumentParser documentParser;
        private final EmbeddingPort embeddingPort;
        private final DocumentChunkWriter documentChunkWriter;

        public Default(
                DocumentStorage documentStorage,
                DocumentRegistryPort documentRegistryPort,
                DocumentParser documentParser,
                EmbeddingPort embeddingPort,
                DocumentChunkWriter documentChunkWriter) {
            this.documentStorage = Objects.requireNonNull(documentStorage, "documentStorage");
            this.documentRegistryPort = Objects.requireNonNull(documentRegistryPort, "documentRegistryPort");
            this.documentParser = Objects.requireNonNull(documentParser, "documentParser");
            this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
            this.documentChunkWriter = Objects.requireNonNull(documentChunkWriter, "documentChunkWriter");
        }

        @Override
        public IngestionResult handle(IngestionCommand command) {
            String indexName = IndexNamingPolicy.indexNameFor(command.filename());
            StoredDocument storedDocument = documentStorage.store(command.filename(), command.content(), command.directoryPath());
            DocumentFileRecord processingDocument = documentRegistryPort.saveDocument(new DocumentFileRecord(
                    null,
                    command.filename(),
                    indexName,
                    storedDocument.storagePath(),
                    IngestionStatus.PROCESSING,
                    Instant.now(),
                    Instant.now()));
            IngestionJobRecord processingJob = documentRegistryPort.saveIngestionJob(new IngestionJobRecord(
                    null,
                    processingDocument.id(),
                    indexName,
                    IngestionStatus.PROCESSING,
                    Instant.now(),
                    Instant.now()));

            try {
                ParsedDocument parsedDocument = documentParser.parse(new ParserRequest(
                        command.filename(),
                        indexName,
                        storedDocument.storagePath()));
                List<String> sentences = parsedDocument.chunks().stream()
                        .map(ParsedChunk::sentenceText)
                        .toList();
                List<List<Float>> embeddings = sentences.isEmpty() ? List.of() : embeddingPort.embedAll(sentences);
                if (!parsedDocument.chunks().isEmpty() && !embeddings.isEmpty()) {
                    documentChunkWriter.ensureIndexExists(indexName, embeddings.get(0).size());
                    documentChunkWriter.writeChunks(indexName, parsedDocument.chunks(), embeddings);
                }

                documentRegistryPort.saveDocument(new DocumentFileRecord(
                        processingDocument.id(),
                        processingDocument.filename(),
                        processingDocument.indexName(),
                        processingDocument.storagePath(),
                        IngestionStatus.COMPLETED,
                        processingDocument.createdAt(),
                        Instant.now()));
                documentRegistryPort.saveIngestionJob(new IngestionJobRecord(
                        processingJob.id(),
                        processingJob.documentFileId(),
                        processingJob.indexName(),
                        IngestionStatus.COMPLETED,
                        processingJob.createdAt(),
                        Instant.now()));

                return new IngestionResult(
                        "success",
                        "Files processed successfully",
                        indexName,
                        storedDocument.storagePath());
            } catch (RuntimeException exception) {
                documentRegistryPort.saveDocument(new DocumentFileRecord(
                        processingDocument.id(),
                        processingDocument.filename(),
                        processingDocument.indexName(),
                        processingDocument.storagePath(),
                        IngestionStatus.FAILED,
                        processingDocument.createdAt(),
                        Instant.now()));
                documentRegistryPort.saveIngestionJob(new IngestionJobRecord(
                        processingJob.id(),
                        processingJob.documentFileId(),
                        processingJob.indexName(),
                        IngestionStatus.FAILED,
                        processingJob.createdAt(),
                        Instant.now()));
                throw exception;
            }
        }
    }
}
