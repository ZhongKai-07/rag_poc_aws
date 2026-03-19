package com.huatai.rag.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.api.health.HealthController;
import com.huatai.rag.api.upload.UploadController;
import com.huatai.rag.api.upload.dto.ProcessedFilesResponse;
import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.application.registry.ProcessedFileQueryApplicationService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileCopyUtils;

@WebMvcTest(controllers = {UploadController.class, HealthController.class})
@Import(ApiExceptionHandler.class)
class UploadControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentIngestionApplicationService documentIngestionApplicationService;

    @MockBean
    private ProcessedFileQueryApplicationService processedFileQueryApplicationService;

    @Test
    void postUploadFilesPreservesEndpointContract() throws Exception {
        when(documentIngestionApplicationService.ingest(eq("sample.pdf"), any(byte[].class), eq("./documents/test-batch")))
                .thenReturn(Map.of("status", "success", "message", "Files processed successfully"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload_files")
                        .file(file)
                        .param("directory_path", "./documents/test-batch"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"success\",\"message\":\"Files processed successfully\"}", true));
    }

    @Test
    void getProcessedFilesReturnsBaselineFixture() throws Exception {
        when(processedFileQueryApplicationService.getProcessedFiles()).thenReturn(loadProcessedFilesFixture());

        mockMvc.perform(get("/processed_files"))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/processed-files-response.json"), true));
    }

    @Test
    void getIndexByFilenameReturnsFrontendCompatibleShape() throws Exception {
        when(processedFileQueryApplicationService.getIndexByFilename("PRC Client.pdf"))
                .thenReturn(Map.of("status", "success", "index_name", "2374dcf7"));

        mockMvc.perform(get("/get_index/PRC Client.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"success\",\"index_name\":\"2374dcf7\"}", true));
    }

    @Test
    void healthEndpointReturnsHealthy() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"healthy\"}", true));
    }

    private ProcessedFilesResponse loadProcessedFilesFixture() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(readFixture("fixtures/contracts/processed-files-response.json"), ProcessedFilesResponse.class);
    }

    private String readFixture(String path) throws Exception {
        return new String(FileCopyUtils.copyToByteArray(new ClassPathResource(path).getInputStream()));
    }
}
