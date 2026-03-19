package com.huatai.rag.domain.parser;

public record ParserRequest(
        String fileName,
        String indexName,
        String storagePath) {
}
