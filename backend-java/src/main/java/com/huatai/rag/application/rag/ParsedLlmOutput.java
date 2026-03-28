package com.huatai.rag.application.rag;

import java.util.List;

public record ParsedLlmOutput(String answerText, List<String> suggestedQuestions) {}
