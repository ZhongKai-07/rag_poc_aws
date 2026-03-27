package com.huatai.rag.domain.retrieval;

import java.util.List;
import java.util.Map;

public record RetrievalRequest(
        List<String> indexNames,
        String query,
        SearchMethod searchMethod,
        int vectorLimit,
        int textLimit,
        double vectorScoreThreshold,
        double textScoreThreshold,
        Map<String, String> metadataFilters
) {
    public RetrievalRequest(List<String> indexNames, String query, SearchMethod searchMethod,
                            int vectorLimit, int textLimit,
                            double vectorScoreThreshold, double textScoreThreshold) {
        this(indexNames, query, searchMethod, vectorLimit, textLimit,
                vectorScoreThreshold, textScoreThreshold, null);
    }
}
