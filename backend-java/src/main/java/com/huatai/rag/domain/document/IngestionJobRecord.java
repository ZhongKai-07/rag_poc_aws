package com.huatai.rag.domain.document;

import java.time.Instant;
import java.util.UUID;

public record IngestionJobRecord(
        UUID id,
        UUID documentFileId,
        String indexName,
        IngestionStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
