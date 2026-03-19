package com.huatai.rag.infrastructure.opensearch;

import com.huatai.rag.domain.parser.ParsedAsset;
import com.huatai.rag.domain.parser.ParsedChunk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenSearchChunkMapper {

    public Map<String, Object> toDocument(ParsedChunk chunk, List<Float> embedding) {
        Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put("page_number", chunk.pageNumber());
        metadata.put("section_path", chunk.sectionPath());
        metadata.put("asset_references", chunk.assets().stream().map(ParsedAsset::reference).toList());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("sentence_vector", embedding);
        document.put("paragraph", chunk.paragraphText());
        document.put("sentence", chunk.sentenceText());
        document.put("metadata", metadata);
        return document;
    }
}
