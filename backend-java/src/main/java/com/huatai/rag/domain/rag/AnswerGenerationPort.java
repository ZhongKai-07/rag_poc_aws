package com.huatai.rag.domain.rag;

import java.util.function.Consumer;

public interface AnswerGenerationPort {

    String generateAnswer(String query, String formattedContext);

    void generateAnswerStream(String query, String formattedContext,
                              Consumer<String> tokenConsumer);
}
