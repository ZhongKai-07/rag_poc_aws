package com.huatai.rag.domain.rag;

public interface AnswerGenerationPort {

    String generateAnswer(String query, String formattedContext);
}
