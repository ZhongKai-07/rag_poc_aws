package com.huatai.rag.infrastructure.opensearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenSearchQueryBuilder {

    private OpenSearchQueryBuilder() {}

    public static Map<String, Object> buildVectorQuery(
            List<Float> queryVector, int limit, Map<String, String> metadataFilters) {
        Map<String, Object> knnClause = Map.of(
                "sentence_vector", Map.of(
                        "vector", queryVector,
                        "k", limit));

        boolean hasFilters = metadataFilters != null && !metadataFilters.isEmpty();

        Map<String, Object> query;
        if (hasFilters) {
            List<Map<String, Object>> filterClauses = new ArrayList<>();
            for (var entry : metadataFilters.entrySet()) {
                filterClauses.add(Map.of("term", Map.of("metadata." + entry.getKey(), entry.getValue())));
            }
            query = Map.of("bool", Map.of(
                    "must", List.of(Map.of("knn", knnClause)),
                    "filter", filterClauses));
        } else {
            query = Map.of("knn", knnClause);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", limit);
        body.put("query", query);
        return body;
    }
}
