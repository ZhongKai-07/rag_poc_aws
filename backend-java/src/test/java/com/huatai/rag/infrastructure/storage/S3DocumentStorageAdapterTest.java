package com.huatai.rag.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.infrastructure.config.StorageProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3DocumentStorageAdapterTest {

    @Test
    void storesUploadedFileInConfiguredBucketAndPrefix() throws IOException {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setDocumentBucket("huatai-rag-docs");
        storageProperties.setDocumentPrefix("incoming");
        S3DocumentStorageAdapter adapter = new S3DocumentStorageAdapter(s3Client, storageProperties);

        DocumentIngestionApplicationService.StoredDocument storedDocument = adapter.store(
                "sample.pdf",
                "pdf-content".getBytes(StandardCharsets.UTF_8),
                "batch-a/contracts");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("huatai-rag-docs");
        assertThat(requestCaptor.getValue().key()).isEqualTo("incoming/batch-a/contracts/sample.pdf");
        assertThat(new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("pdf-content");
        assertThat(storedDocument.storagePath()).isEqualTo("s3://huatai-rag-docs/incoming/batch-a/contracts/sample.pdf");
    }

    @Test
    void normalizesBlankAndSlashPaddedDirectoryPaths() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setDocumentBucket("huatai-rag-docs");
        storageProperties.setDocumentPrefix("/incoming/");
        S3DocumentStorageAdapter adapter = new S3DocumentStorageAdapter(s3Client, storageProperties);

        DocumentIngestionApplicationService.StoredDocument blankDirectory = adapter.store(
                "sample.pdf",
                new byte[] {1, 2, 3},
                "   ");
        DocumentIngestionApplicationService.StoredDocument paddedDirectory = adapter.store(
                "sample.pdf",
                new byte[] {1, 2, 3},
                "/batch-a//nested/");

        assertThat(blankDirectory.storagePath()).isEqualTo("s3://huatai-rag-docs/incoming/sample.pdf");
        assertThat(paddedDirectory.storagePath()).isEqualTo("s3://huatai-rag-docs/incoming/batch-a/nested/sample.pdf");
    }
}
