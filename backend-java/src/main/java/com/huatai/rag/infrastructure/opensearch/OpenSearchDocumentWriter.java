package com.huatai.rag.infrastructure.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.parser.ParsedChunk;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

public class OpenSearchDocumentWriter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OpenSearchChunkMapper chunkMapper;
    private final OpenSearchIndexManager indexManager;

    public OpenSearchDocumentWriter(
            RestClient restClient,
            ObjectMapper objectMapper,
            OpenSearchChunkMapper chunkMapper,
            OpenSearchIndexManager indexManager) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chunkMapper = chunkMapper;
        this.indexManager = indexManager;
    }

    public void writeChunks(String indexName, List<ParsedChunk> chunks, List<List<Float>> embeddings) {
        if (chunks.isEmpty()) {
            return;
        }

        if (restClient == null) {
            return;
        }

        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> document = chunkMapper.toDocument(chunks.get(i), embeddings.get(i));
            payload.append("{\"index\":{\"_index\":\"").append(indexName).append("\"}}\n");
            try {
                payload.append(objectMapper.writeValueAsString(document)).append("\n");
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to serialize OpenSearch bulk document", exception);
            }
        }

        Request request = new Request("POST", "/_bulk");
        request.setJsonEntity(payload.toString());
        try {
            restClient.performRequest(request);
            restClient.performRequest(new Request("POST", "/" + indexName + "/_refresh"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write OpenSearch bulk payload", exception);
        }
    }
}
