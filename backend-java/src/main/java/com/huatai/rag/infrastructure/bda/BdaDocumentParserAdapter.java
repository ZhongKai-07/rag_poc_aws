package com.huatai.rag.infrastructure.bda;

import com.huatai.rag.domain.parser.DocumentParser;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParserRequest;
import com.huatai.rag.infrastructure.config.StorageProperties;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class BdaDocumentParserAdapter implements DocumentParser {

    private final BdaClient bdaClient;
    private final BdaResultMapper resultMapper;
    private final StorageProperties storageProperties;

    public BdaDocumentParserAdapter(BdaClient bdaClient, BdaResultMapper resultMapper, StorageProperties storageProperties) {
        this.bdaClient = Objects.requireNonNull(bdaClient, "bdaClient");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
        this.storageProperties = Objects.requireNonNull(storageProperties, "storageProperties");
    }

    @Override
    public ParsedDocument parse(ParserRequest request) {
        String outputUri = defaultOutputUri(request.storagePath(), request.indexName());
        return resultMapper.map(
                bdaClient.parse(request.storagePath(), outputUri),
                request.fileName(),
                request.indexName(),
                outputUri);   // new 4th argument
    }

    private String defaultOutputUri(String storagePath, String indexName) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        String bucket = requireText(storageProperties.getDocumentBucket(), "documentBucket");
        String prefix = joinSegments(storageProperties.getBdaOutputPrefix(), indexName + ".json");
        return "s3://" + bucket + "/" + prefix;
    }

    private String joinSegments(String... segments) {
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            String normalized = normalizeSegment(segment);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(normalized);
        }
        return result.toString();
    }

    private String normalizeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("^/+", "");
        normalized = normalized.replaceAll("/+$", "");
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
