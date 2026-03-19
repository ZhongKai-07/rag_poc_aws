package com.huatai.rag.api.question;

import com.huatai.rag.api.question.dto.TopQuestionsResponse;
import com.huatai.rag.application.history.QuestionHistoryApplicationService;
import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuestionController {

    private final QuestionHistoryApplicationService questionHistoryApplicationService;

    public QuestionController(QuestionHistoryApplicationService questionHistoryApplicationService) {
        this.questionHistoryApplicationService = questionHistoryApplicationService;
    }

    @GetMapping("/top_questions/{index_name}")
    public TopQuestionsResponse getTopQuestions(
            @PathVariable("index_name") String indexName,
            @RequestParam(name = "top_n", defaultValue = "5") int topN) {
        return toResponse(questionHistoryApplicationService.getTopQuestionsView(indexName, topN));
    }

    @GetMapping("/top_questions_multi")
    public TopQuestionsResponse getTopQuestionsMulti(
            @RequestParam("index_names") String indexNames,
            @RequestParam(name = "top_n", defaultValue = "5") int topN) {
        return toResponse(questionHistoryApplicationService.getTopQuestionsView(
                Arrays.stream(indexNames.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList(),
                topN));
    }

    private TopQuestionsResponse toResponse(QuestionHistoryApplicationService.TopQuestionsResult result) {
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
