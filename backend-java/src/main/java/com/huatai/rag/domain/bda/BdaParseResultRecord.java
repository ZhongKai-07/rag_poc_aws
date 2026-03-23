package com.huatai.rag.domain.bda;

import java.time.Instant;
import java.util.UUID;

public record BdaParseResultRecord(
        UUID id,
        UUID documentFileId,
        String indexName,
        String s3OutputPath,
        int chunkCount,
        int pageCount,
        String parserType,
        String parserVersion,
        Instant createdAt) {
}
