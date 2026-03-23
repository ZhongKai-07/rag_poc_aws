package com.huatai.rag.domain.parser;

import java.util.List;

public record ParsedDocument(
        String fileName,
        String indexName,
        List<ParsedPage> pages,
        List<ParsedChunk> chunks,
        List<ParsedAsset> assets,
        String s3OutputPath,
        String parserType,
        String parserVersion) {

    public ParsedDocument {
        pages = List.copyOf(pages);
        chunks = List.copyOf(chunks);
        assets = List.copyOf(assets);
    }

    public String parserProvenance() {
        return parserType + ":" + parserVersion;
    }
}
