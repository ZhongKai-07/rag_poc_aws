package com.huatai.rag.infrastructure.storage;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.infrastructure.config.StorageProperties;
import java.util.Objects;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3DocumentStorageAdapter implements DocumentIngestionApplicationService.DocumentStorage {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    public S3DocumentStorageAdapter(S3Client s3Client, StorageProperties storageProperties) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.storageProperties = Objects.requireNonNull(storageProperties, "storageProperties");
    }

    @Override
    public DocumentIngestionApplicationService.StoredDocument store(String filename, byte[] content, String directoryPath) {
        String bucket = requireText(storageProperties.getDocumentBucket(), "documentBucket");
        String key = joinKeySegments(storageProperties.getDocumentPrefix(), directoryPath, filename);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                RequestBody.fromBytes(content == null ? new byte[0] : content));
        return new DocumentIngestionApplicationService.StoredDocument(filename, "s3://" + bucket + "/" + key);
    }

    private static String joinKeySegments(String... segments) {
        StringBuilder key = new StringBuilder();
        for (String segment : segments) {
            String normalized = normalizeSegment(segment);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (key.length() > 0) {
                key.append('/');
            }
            key.append(normalized);
        }
        return key.toString();
    }

    private static String normalizeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("^/+", "");
        normalized = normalized.replaceAll("/+$", "");
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
