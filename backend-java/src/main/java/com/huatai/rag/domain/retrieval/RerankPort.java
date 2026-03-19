package com.huatai.rag.domain.retrieval;

import java.util.List;

public interface RerankPort {

    List<RetrievedDocument> rerank(String query, List<RetrievedDocument> documents, double rerankScoreThreshold);
}
