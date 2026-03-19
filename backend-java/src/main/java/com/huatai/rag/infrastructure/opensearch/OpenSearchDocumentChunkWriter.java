package com.huatai.rag.infrastructure.opensearch;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.domain.parser.ParsedChunk;
import java.util.List;
import java.util.Objects;

public class OpenSearchDocumentChunkWriter implements DocumentIngestionApplicationService.DocumentChunkWriter {

    private final OpenSearchIndexManager openSearchIndexManager;
    private final OpenSearchDocumentWriter openSearchDocumentWriter;

    public OpenSearchDocumentChunkWriter(
            OpenSearchIndexManager openSearchIndexManager,
            OpenSearchDocumentWriter openSearchDocumentWriter) {
        this.openSearchIndexManager = Objects.requireNonNull(openSearchIndexManager, "openSearchIndexManager");
        this.openSearchDocumentWriter = Objects.requireNonNull(openSearchDocumentWriter, "openSearchDocumentWriter");
    }

    @Override
    public void ensureIndexExists(String indexName, int embeddingDimension) {
        openSearchIndexManager.ensureIndex(indexName, embeddingDimension);
    }

    @Override
    public void writeChunks(String indexName, List<ParsedChunk> chunks, List<List<Float>> embeddings) {
        openSearchDocumentWriter.writeChunks(indexName, chunks, embeddings);
    }
}
