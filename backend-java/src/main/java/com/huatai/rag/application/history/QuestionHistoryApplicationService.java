package com.huatai.rag.application.history;

import com.huatai.rag.api.question.dto.TopQuestionsResponse;
import com.huatai.rag.domain.history.QuestionHistoryPort;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public interface QuestionHistoryApplicationService {

    default TopQuestionsResponse getTopQuestions(String indexName, int topN) {
        return TopQuestionsResult.toApiResponse(getTopQuestionsView(indexName, topN));
    }

    default TopQuestionsResponse getTopQuestionsMulti(String indexNames, int topN) {
        List<String> parsedIndexNames = Arrays.stream(indexNames.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return TopQuestionsResult.toApiResponse(getTopQuestionsView(parsedIndexNames, topN));
    }

    TopQuestionsResult getTopQuestionsView(String indexName, int topN);

    TopQuestionsResult getTopQuestionsView(List<String> indexNames, int topN);

    record QuestionCountResult(String question, long count) {
    }

    record TopQuestionsResult(String status, List<QuestionCountResult> questions) {

        public TopQuestionsResult {
            questions = List.copyOf(questions);
        }

        private static TopQuestionsResponse toApiResponse(TopQuestionsResult result) {
            TopQuestionsResponse response = new TopQuestionsResponse();
            response.setStatus(result.status());
            response.setQuestions(result.questions().stream().map(question -> {
                TopQuestionsResponse.QuestionCount questionCount = new TopQuestionsResponse.QuestionCount();
                questionCount.setQuestion(question.question());
                questionCount.setCount(Math.toIntExact(question.count()));
                return questionCount;
            }).toList());
            return response;
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
