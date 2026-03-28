package com.huatai.rag.domain.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatFeedback(UUID id, UUID messageId, UUID sessionId,
                           String rating, String comment, Instant createdAt) {}
