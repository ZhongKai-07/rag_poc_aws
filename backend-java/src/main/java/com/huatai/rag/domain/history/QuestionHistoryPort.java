package com.huatai.rag.domain.history;

import java.util.List;

public interface QuestionHistoryPort {

    void recordQuestion(String indexName, String question);

    List<QuestionCount> topQuestions(String indexName, int limit);

    List<QuestionCount> topQuestionsMulti(List<String> indexNames, int limit);

    record QuestionCount(String question, long count) {
    }
}
