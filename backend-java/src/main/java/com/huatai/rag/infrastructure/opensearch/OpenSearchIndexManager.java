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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexManager.class);
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
        Map<String, Object> metadataProperties = new LinkedHashMap<>();
        metadataProperties.put("counterparty", Map.of("type", "keyword"));
        metadataProperties.put("agreement_type", Map.of("type", "keyword"));
        metadataProperties.put("filename", Map.of("type", "keyword"));
        metadataProperties.put("page_number", Map.of("type", "integer"));
        metadataProperties.put("section_path", Map.of("type", "keyword"));
        properties.put("metadata", Map.of("type", "object", "dynamic", true, "properties", metadataProperties));

        return Map.of(
                "settings", Map.of("index", Map.of("knn", true)),
                "mappings", Map.of("properties", properties));
    }

    /**
     * Ensures the index exists with correct knn_vector mapping.
     * Uses GET /_mapping as the single source of truth for both existence and validation,
     * avoiding HEAD+GET inconsistencies observed with the OpenSearch REST client over HTTP/2.
     */
    public void ensureIndex(String indexName, int dimension) {
        if (restClient == null) {
            return;
        }

        MappingStatus status = checkMappingStatus(indexName);

        switch (status) {
            case VALID:
                log.info("Index {} exists with correct knn_vector mapping", indexName);
                break;
            case NOT_FOUND:
                log.info("Index {} does not exist, creating with knn_vector mapping (dimension={})", indexName, dimension);
                createIndex(indexName, dimension);
                break;
            case INVALID:
                log.warn("Index {} exists but sentence_vector is not knn_vector — deleting and recreating", indexName);
                deleteIndexIfExists(indexName);
                createIndex(indexName, dimension);
                break;
        }
    }

    private enum MappingStatus { VALID, INVALID, NOT_FOUND }

    private MappingStatus checkMappingStatus(String indexName) {
        try {
            var response = restClient.performRequest(new Request("GET", "/" + indexName + "/_mapping"));
            Map<String, Object> body = objectMapper.readValue(
                    response.getEntity().getContent(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> indexBody = (Map<String, Object>) body.get(indexName);
            if (indexBody == null) return MappingStatus.INVALID;
            @SuppressWarnings("unchecked")
            Map<String, Object> mappings = (Map<String, Object>) indexBody.get("mappings");
            if (mappings == null) return MappingStatus.INVALID;
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
            if (properties == null) return MappingStatus.INVALID;
            @SuppressWarnings("unchecked")
            Map<String, Object> vectorField = (Map<String, Object>) properties.get("sentence_vector");
            if (vectorField == null) return MappingStatus.INVALID;
            return "knn_vector".equals(vectorField.get("type")) ? MappingStatus.VALID : MappingStatus.INVALID;
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return MappingStatus.NOT_FOUND;
            }
            log.warn("Unexpected error checking mapping for index {}, will recreate", indexName, exception);
            return MappingStatus.NOT_FOUND;
        } catch (IOException exception) {
            log.warn("Could not verify mapping for index {}, will recreate", indexName, exception);
            return MappingStatus.NOT_FOUND;
        }
    }

    private void deleteIndexIfExists(String indexName) {
        try {
            restClient.performRequest(new Request("DELETE", "/" + indexName));
            log.info("Deleted index {}", indexName);
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                log.info("Index {} already gone, skipping delete", indexName);
            } else {
                throw new IllegalStateException("Failed to delete OpenSearch index " + indexName, exception);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete OpenSearch index " + indexName, exception);
        }
    }

    private void createIndex(String indexName, int dimension) {
        Request request = new Request("PUT", "/" + indexName);
        try {
            String mappingJson = objectMapper.writeValueAsString(buildIndexMapping(dimension));
            log.info("Creating index {} with mapping: {}", indexName, mappingJson);
            request.setJsonEntity(mappingJson);
            restClient.performRequest(request);
            log.info("Index {} created successfully", indexName);
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
