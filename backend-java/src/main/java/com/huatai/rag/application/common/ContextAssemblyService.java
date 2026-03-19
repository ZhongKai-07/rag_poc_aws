package com.huatai.rag.application.common;

import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import java.util.List;

public class ContextAssemblyService {

    public List<RetrievedDocument> selectSourceDocuments(
            RetrievalResult retrievalResult,
            List<RetrievedDocument> rerankedDocuments) {
        if (rerankedDocuments != null && !rerankedDocuments.isEmpty()) {
            return List.copyOf(rerankedDocuments);
        }
        if (retrievalResult != null && !retrievalResult.rerankDocuments().isEmpty()) {
            return retrievalResult.rerankDocuments();
        }
        return retrievalResult == null ? List.of() : retrievalResult.recallDocuments();
    }
}
