package com.huatai.rag.infrastructure.opensearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.document.IndexNamingPolicy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;

public class OpenSearchIndexManager {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenSearchIndexManager(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public static String compatibleIndexName(String filename) {
        return IndexNamingPolicy.indexNameFor(filename);
    }

    public Map<String, Object> buildIndexMapping(int dimension) {
        Map<String, Object> vectorField = new LinkedHashMap<>();
        vectorField.put("type", "knn_vector");
        vectorField.put("dimension", dimension);
        vectorField.put("method", Map.of(
                "name", "hnsw",
                "space_type", "l2",
                "engine", "faiss"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sentence_vector", vectorField);
        properties.put("paragraph", Map.of("type", "text"));
        properties.put("sentence", Map.of("type", "text"));
        properties.put("metadata", Map.of("type", "object", "dynamic", true));

        return Map.of(
                "settings", Map.of("index", Map.of("knn", true)),
                "mappings", Map.of("properties", properties));
    }

    public void ensureIndex(String indexName, int dimension) {
        if (restClient == null) {
            return;
        }

        try {
            restClient.performRequest(new Request("HEAD", "/" + indexName));
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() != 404) {
                throw new IllegalStateException("Failed to check OpenSearch index " + indexName, exception);
            }
            createIndex(indexName, dimension);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to check OpenSearch index " + indexName, exception);
        }
    }

    private void createIndex(String indexName, int dimension) {
        Request request = new Request("PUT", "/" + indexName);
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(buildIndexMapping(dimension)));
            restClient.performRequest(request);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create OpenSearch index " + indexName, exception);
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> readResponse(org.opensearch.client.Response response) throws IOException {
        return objectMapper.readValue(response.getEntity().getContent(), new TypeReference<>() {
        });
    }
}
