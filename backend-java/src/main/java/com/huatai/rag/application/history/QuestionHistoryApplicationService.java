package com.huatai.rag.application.history;

import com.huatai.rag.domain.history.QuestionHistoryPort;
import java.util.List;
import java.util.Objects;

public interface QuestionHistoryApplicationService {

    TopQuestionsResult getTopQuestionsView(String indexName, int topN);

    TopQuestionsResult getTopQuestionsView(List<String> indexNames, int topN);

    record QuestionCountResult(String question, long count) {
    }

    record TopQuestionsResult(String status, List<QuestionCountResult> questions) {

        public TopQuestionsResult {
            questions = List.copyOf(questions);
        }
    }

    final class Default implements QuestionHistoryApplicationService {
        private final QuestionHistoryPort questionHistoryPort;

        public Default(QuestionHistoryPort questionHistoryPort) {
            this.questionHistoryPort = Objects.requireNonNull(questionHistoryPort, "questionHistoryPort");
        }

        @Override
        public TopQuestionsResult getTopQuestionsView(String indexName, int topN) {
            return new TopQuestionsResult(
                    "success",
                    questionHistoryPort.topQuestions(indexName, topN).stream()
                            .map(result -> new QuestionCountResult(result.question(), result.count()))
                            .toList());
        }

        @Override
        public TopQuestionsResult getTopQuestionsView(List<String> indexNames, int topN) {
            return new TopQuestionsResult(
                    "success",
                    questionHistoryPort.topQuestionsMulti(indexNames, topN).stream()
                            .map(result -> new QuestionCountResult(result.question(), result.count()))
                            .toList());
        }
    }
}
