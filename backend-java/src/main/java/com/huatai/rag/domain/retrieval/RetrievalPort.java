package com.huatai.rag.domain.retrieval;

public interface RetrievalPort {

    RetrievalResult retrieve(RetrievalRequest request);
}
