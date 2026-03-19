package com.huatai.rag.application.history;

import com.huatai.rag.api.question.dto.TopQuestionsResponse;

public interface QuestionHistoryApplicationService {

    TopQuestionsResponse getTopQuestions(String indexName, int topN);

    TopQuestionsResponse getTopQuestionsMulti(String indexNames, int topN);
}
