package com.huatai.rag.domain.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatSession(UUID id, String title, String module,
                          Instant createdAt, Instant updatedAt) {}
