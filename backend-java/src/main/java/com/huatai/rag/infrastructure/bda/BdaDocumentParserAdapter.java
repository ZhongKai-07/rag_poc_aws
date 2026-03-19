package com.huatai.rag.infrastructure.bda;

import com.huatai.rag.domain.parser.DocumentParser;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParserRequest;
import java.util.Objects;

public class BdaDocumentParserAdapter implements DocumentParser {

    private final BdaClient bdaClient;
    private final BdaResultMapper resultMapper;

    public BdaDocumentParserAdapter(BdaClient bdaClient, BdaResultMapper resultMapper) {
        this.bdaClient = Objects.requireNonNull(bdaClient, "bdaClient");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
    }

    @Override
    public ParsedDocument parse(ParserRequest request) {
        String outputUri = defaultOutputUri(request.storagePath(), request.indexName());
        return resultMapper.map(
                bdaClient.parse(request.storagePath(), outputUri),
                request.fileName(),
                request.indexName());
    }

    private String defaultOutputUri(String storagePath, String indexName) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        String normalized = storagePath.endsWith("/") ? storagePath.substring(0, storagePath.length() - 1) : storagePath;
        return normalized + "/bda-output/" + indexName + ".json";
    }
}
