package com.huatai.rag.domain.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentFileRecord(
        UUID id,
        String filename,
        String indexName,
        String storagePath,
        IngestionStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
