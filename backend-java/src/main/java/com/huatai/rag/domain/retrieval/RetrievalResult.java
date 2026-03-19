package com.huatai.rag.domain.retrieval;

import java.util.List;

public record RetrievalResult(
        List<RetrievedDocument> recallDocuments,
        List<RetrievedDocument> rerankDocuments) {

    public RetrievalResult {
        recallDocuments = List.copyOf(recallDocuments);
        rerankDocuments = List.copyOf(rerankDocuments);
    }

    public List<RetrievedDocument> sourceDocuments() {
        return rerankDocuments;
    }
}
