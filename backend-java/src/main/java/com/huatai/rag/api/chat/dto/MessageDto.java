package com.huatai.rag.api.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huatai.rag.domain.rag.Citation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageDto(
        UUID id,
        String role,
        String content,
        List<Citation> citations,
        @JsonProperty("suggested_questions") List<String> suggestedQuestions,
        FeedbackDto feedback,
        @JsonProperty("created_at") Instant createdAt
) {}
