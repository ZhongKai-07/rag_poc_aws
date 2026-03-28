package com.huatai.rag.api.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionDetailDto(
        UUID id,
        String title,
        String module,
        @JsonProperty("created_at") Instant createdAt,
        List<MessageDto> messages
) {}
