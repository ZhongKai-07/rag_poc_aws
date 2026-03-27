package com.huatai.rag.infrastructure.opensearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import com.huatai.rag.domain.retrieval.RetrievalPort;
import com.huatai.rag.domain.retrieval.RetrievalRequest;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.SearchMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Logger imported for both outer class and inner RestClientSearchGateway

public class OpenSearchRetrievalAdapter implements RetrievalPort {

    public interface SearchGateway {
        List<RetrievedDocument> vectorSearch(List<String> indexNames, String query, int limit, double scoreThreshold);

        default List<RetrievedDocument> vectorSearch(List<String> indexNames, String query, int limit, double scoreThreshold, Map<String, String> metadataFilters) {
            return vectorSearch(indexNames, query, limit, scoreThreshold);
        }

        List<RetrievedDocument> textSearch(List<String> indexNames, String query, int limit, double scoreThreshold);
    }

    private final SearchGateway searchGateway;

    public OpenSearchRetrievalAdapter(SearchGateway searchGateway) {
        this.searchGateway = searchGateway;
    }

    public OpenSearchRetrievalAdapter(RestClient restClient, ObjectMapper objectMapper, EmbeddingPort embeddingPort) {
        this.searchGateway = new RestClientSearchGateway(restClient, objectMapper, embeddingPort);
    }

    private static final Logger log = LoggerFactory.getLogger(OpenSearchRetrievalAdapter.class);

    @Override
    public RetrievalResult retrieve(RetrievalRequest request) {
        log.info("[Retrieval] indices={} method={} query='{}' filters={}",
                request.indexNames(), request.searchMethod(), request.query(), request.metadataFilters());
        List<RetrievedDocument> results = switch (request.searchMethod()) {
            case VECTOR -> searchGateway.vectorSearch(
                    request.indexNames(), request.query(),
                    request.vectorLimit(), request.vectorScoreThreshold(),
                    request.metadataFilters());
            case TEXT -> searchGateway.textSearch(
                    request.indexNames(), request.query(),
                    request.textLimit(), request.textScoreThreshold());
            case MIX -> mergeUnique(
                    searchGateway.vectorSearch(
                            request.indexNames(), request.query(),
                            request.vectorLimit(), request.vectorScoreThreshold(),
                            request.metadataFilters()),
                    searchGateway.textSearch(
                            request.indexNames(), request.query(),
                            request.textLimit(), request.textScoreThreshold()),
                    request.vectorLimit() + request.textLimit());
        };

        log.info("[Retrieval] returned {} documents", results.size());
        return new RetrievalResult(results, results);
    }

    private List<RetrievedDocument> mergeUnique(
            List<RetrievedDocument> vectorResults,
            List<RetrievedDocument> textResults,
            int maxSize) {
        Map<String, RetrievedDocument> unique = new LinkedHashMap<>();
        for (RetrievedDocument document : vectorResults) {
            unique.putIfAbsent(document.pageContent(), document);
        }
        for (RetrievedDocument document : textResults) {
            unique.putIfAbsent(document.pageContent(), document);
        }
        return unique.values().stream().limit(maxSize).toList();
    }

    private static final class RestClientSearchGateway implements SearchGateway {
        private static final Logger log = LoggerFactory.getLogger(RestClientSearchGateway.class);
        private final RestClient restClient;
        private final ObjectMapper objectMapper;
        private final EmbeddingPort embeddingPort;

        private RestClientSearchGateway(RestClient restClient, ObjectMapper objectMapper, EmbeddingPort embeddingPort) {
            this.restClient = restClient;
            this.objectMapper = objectMapper;
            this.embeddingPort = embeddingPort;
        }

        @Override
        public List<RetrievedDocument> vectorSearch(List<String> indexNames, String query, int limit, double scoreThreshold) {
            return vectorSearch(indexNames, query, limit, scoreThreshold, null);
        }

        @Override
        public List<RetrievedDocument> vectorSearch(List<String> indexNames, String query, int limit, double scoreThreshold, Map<String, String> metadataFilters) {
            if (embeddingPort == null) {
                throw new IllegalStateException("EmbeddingPort is required for vector search");
            }

            List<Float> queryVector = embeddingPort.embedAll(List.of(query)).get(0);
            Map<String, Object> body = OpenSearchQueryBuilder.buildVectorQuery(queryVector, limit, metadataFilters);
            return executeSearch(indexNames, body, scoreThreshold, true);
        }

        @Override
        public List<RetrievedDocument> textSearch(List<String> indexNames, String query, int limit, double scoreThreshold) {
            Map<String, Object> body = Map.of(
                    "size", limit,
                    "query", Map.of(
                            "match", Map.of(
                                    "sentence", query)));
            return executeSearch(indexNames, body, scoreThreshold, false);
        }

        private List<RetrievedDocument> executeSearch(
                List<String> indexNames,
                Map<String, Object> body,
                double scoreThreshold,
                boolean vectorSearch) {
            String joinedIndices = String.join(",", indexNames);
            Request request = new Request("POST", "/" + joinedIndices + "/_search");
            try {
                request.setJsonEntity(objectMapper.writeValueAsString(body));
                var response = restClient.performRequest(request);
                Map<String, Object> responseBody = objectMapper.readValue(
                        response.getEntity().getContent(),
                        new TypeReference<>() {
                        });
                return parseHits(responseBody, scoreThreshold, vectorSearch);
            } catch (ResponseException exception) {
                log.error("OpenSearch search failed for {} with status {}",
                        joinedIndices,
                        exception.getResponse().getStatusLine().getStatusCode(),
                        exception);
                throw new IllegalStateException("Failed to execute OpenSearch search", exception);
            } catch (IOException exception) {
                log.error("OpenSearch search I/O failure for {}", joinedIndices, exception);
                throw new IllegalStateException("Failed to execute OpenSearch search", exception);
            }
        }

        @SuppressWarnings("unchecked")
        private List<RetrievedDocument> parseHits(
                Map<String, Object> responseBody,
                double scoreThreshold,
                boolean vectorSearch) {
            Map<String, Object> hitsContainer = (Map<String, Object>) responseBody.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsContainer.getOrDefault("hits", List.of());
            List<RetrievedDocument> documents = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                double score = ((Number) hit.getOrDefault("_score", 0.0)).doubleValue();
                double normalizedScore = vectorSearch ? score * 100 : score;
                if (normalizedScore < scoreThreshold) {
                    continue;
                }
                documents.add(new RetrievedDocument(
                        (String) source.getOrDefault("paragraph", ""),
                        normalizedScore,
                        null,
                        (Map<String, Object>) source.getOrDefault("metadata", Map.of())));
            }
            return documents;
        }
    }
}
