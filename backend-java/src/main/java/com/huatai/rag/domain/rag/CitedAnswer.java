package com.huatai.rag.domain.rag;

import java.util.List;

public record CitedAnswer(
        String answer,
        List<Citation> citations,
        List<String> suggestedQuestions
) {}
