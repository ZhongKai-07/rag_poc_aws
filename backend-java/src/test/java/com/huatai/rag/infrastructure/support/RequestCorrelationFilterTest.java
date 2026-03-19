package com.huatai.rag.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.infrastructure.config.StorageProperties;
import com.huatai.rag.infrastructure.storage.LocalFileStorageAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestCorrelationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestCorrelationFilter requestCorrelationFilter;

    @Autowired
    private DocumentIngestionApplicationService documentIngestionApplicationService;

    @Test
    void filterAddsOrPreservesRequestCorrelationHeader() throws Exception {
        assertThat(requestCorrelationFilter).isNotNull();
        assertThat(documentIngestionApplicationService).isNotNull();

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));

        mockMvc.perform(get("/health").header("X-Request-Id", "req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-123"));
    }

    @Test
    void localFileStorageAdapterStoresFileInsideConfiguredRoot(@TempDir Path tempDir) throws Exception {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setDocumentRoot(tempDir.toString());
        LocalFileStorageAdapter adapter = new LocalFileStorageAdapter(storageProperties);

        DocumentIngestionApplicationService.StoredDocument storedDocument = adapter.store(
                "sample.pdf",
                "pdf-content".getBytes(StandardCharsets.UTF_8),
                "uploads");

        Path storedPath = Path.of(storedDocument.storagePath());
        assertThat(storedPath).exists();
        assertThat(storedPath.startsWith(tempDir)).isTrue();
        assertThat(Files.readString(storedPath)).isEqualTo("pdf-content");
    }
}
