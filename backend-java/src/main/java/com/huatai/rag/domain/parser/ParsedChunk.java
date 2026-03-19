package com.huatai.rag.domain.parser;

import java.util.List;
import java.util.Map;

public record ParsedChunk(
        String chunkId,
        int pageNumber,
        String paragraphText,
        String sentenceText,
        List<String> sectionPath,
        List<ParsedAsset> assets,
        Map<String, String> metadata) {

    public ParsedChunk {
        sectionPath = List.copyOf(sectionPath);
        assets = List.copyOf(assets);
        metadata = Map.copyOf(metadata);
    }
}
