package com.huatai.rag.domain.rag;

import com.huatai.rag.domain.retrieval.RetrievedDocument;
import java.util.List;

public interface AnswerGenerationPort {

    String generateAnswer(String query, List<RetrievedDocument> sourceDocuments);
}
