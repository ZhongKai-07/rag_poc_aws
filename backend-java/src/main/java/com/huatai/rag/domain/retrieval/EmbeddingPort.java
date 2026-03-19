package com.huatai.rag.domain.retrieval;

import java.util.List;

public interface EmbeddingPort {

    List<List<Float>> embedAll(List<String> texts);
}
