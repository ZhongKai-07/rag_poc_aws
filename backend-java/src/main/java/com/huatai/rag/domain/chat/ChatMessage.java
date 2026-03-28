package com.huatai.rag.domain.chat;

import com.huatai.rag.domain.rag.Citation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessage(UUID id, UUID sessionId, String role, String content,
                          List<Citation> citations, List<String> suggestedQuestions,
                          Instant createdAt) {}
