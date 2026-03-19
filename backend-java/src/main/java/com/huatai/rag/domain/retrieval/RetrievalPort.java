package com.huatai.rag.domain.retrieval;

import java.util.List;

public interface RetrievalPort {

    RetrievalResult retrieve(
            List<String> indexNames,
            String query,
            SearchMethod searchMethod,
            int vectorLimit,
            int textLimit,
            double vectorScoreThreshold,
            double textScoreThreshold);
}
