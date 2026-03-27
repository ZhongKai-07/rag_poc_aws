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
import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private static final Logger log = LoggerFactory.getLogger(Default.class);
        private final DocumentStorage documentStorage;
        private final DocumentRegistryPort documentRegistryPort;
        private final DocumentParser documentParser;
        private final EmbeddingPort embeddingPort;
        private final DocumentChunkWriter documentChunkWriter;
        private final BdaParseResultPort bdaParseResultPort;

        public Default(
                DocumentStorage documentStorage,
                DocumentRegistryPort documentRegistryPort,
                DocumentParser documentParser,
                EmbeddingPort embeddingPort,
                DocumentChunkWriter documentChunkWriter,
                BdaParseResultPort bdaParseResultPort) {
            this.documentStorage = Objects.requireNonNull(documentStorage, "documentStorage");
            this.documentRegistryPort = Objects.requireNonNull(documentRegistryPort, "documentRegistryPort");
            this.documentParser = Objects.requireNonNull(documentParser, "documentParser");
            this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
            this.documentChunkWriter = Objects.requireNonNull(documentChunkWriter, "documentChunkWriter");
            this.bdaParseResultPort = Objects.requireNonNull(bdaParseResultPort, "bdaParseResultPort");
        }

        @Override
        public IngestionResult handle(IngestionCommand command) {
            Optional<DocumentFileRecord> existing = documentRegistryPort.findByFilename(command.filename());
            if (existing.isPresent() && existing.get().status() == IngestionStatus.COMPLETED) {
                DocumentFileRecord completed = existing.get();
                return new IngestionResult(
                        "success",
                        "Files processed successfully",
                        completed.indexName(),
                        completed.storagePath());
            }

            String indexName = IndexNamingPolicy.indexNameFor(command.filename());
            StoredDocument storedDocument = documentStorage.store(command.filename(), command.content(), command.directoryPath());

            // Reuse existing record ID for FAILED/PROCESSING retries to avoid unique constraint violation on index_name
            UUID existingId = existing.map(DocumentFileRecord::id).orElse(null);
            DocumentFileRecord processingDocument = documentRegistryPort.saveDocument(new DocumentFileRecord(
                    existingId,
                    command.filename(),
                    indexName,
                    storedDocument.storagePath(),
                    IngestionStatus.PROCESSING,
                    existing.map(DocumentFileRecord::createdAt).orElse(Instant.now()),
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
                log.info("Parsed document {} into {} chunks", command.filename(), parsedDocument.chunks().size());
                // non-blocking: persist BDA parse metadata (failure must not abort ingestion)
                try {
                    bdaParseResultPort.save(new BdaParseResultRecord(
                            null,
                            processingDocument.id(),
                            indexName,
                            parsedDocument.s3OutputPath(),
                            parsedDocument.chunks().size(),
                            parsedDocument.pages().size(),
                            parsedDocument.parserType(),
                            parsedDocument.parserVersion(),
                            Instant.now()));
                } catch (Exception e) {
                    log.warn("Failed to save BDA parse result for index {}: {}", indexName, e.getMessage());
                }
                List<String> sentences = parsedDocument.chunks().stream()
                        .map(ParsedChunk::sentenceText)
                        .toList();
                List<List<Float>> embeddings = sentences.isEmpty() ? List.of() : embeddingPort.embedAll(sentences);
                log.info("Generated {} embeddings for {}", embeddings.size(), command.filename());
                if (!parsedDocument.chunks().isEmpty() && !embeddings.isEmpty()) {
                    List<ParsedChunk> enrichedChunks = parsedDocument.chunks().stream()
                            .map(chunk -> {
                                var metadata = new LinkedHashMap<>(chunk.metadata());
                                metadata.put("filename", command.filename());
                                return new ParsedChunk(chunk.chunkId(), chunk.pageNumber(),
                                        chunk.paragraphText(), chunk.sentenceText(),
                                        chunk.sectionPath(), chunk.assets(), metadata);
                            })
                            .toList();
                    log.info("Writing {} chunks into OpenSearch index {}", enrichedChunks.size(), indexName);
                    documentChunkWriter.ensureIndexExists(indexName, embeddings.get(0).size());
                    documentChunkWriter.writeChunks(indexName, enrichedChunks, embeddings);
                } else {
                    log.warn("Skipping OpenSearch write for {} because chunks or embeddings were empty", command.filename());
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
