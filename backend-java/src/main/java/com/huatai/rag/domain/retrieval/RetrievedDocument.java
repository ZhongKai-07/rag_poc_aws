package com.huatai.rag.domain.retrieval;

import java.util.Map;

public record RetrievedDocument(
        String pageContent,
        double score,
        Double rerankScore,
        Map<String, Object> metadata) {

    public RetrievedDocument {
        metadata = Map.copyOf(metadata);
    }
}
