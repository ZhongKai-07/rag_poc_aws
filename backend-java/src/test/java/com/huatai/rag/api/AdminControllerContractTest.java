package com.huatai.rag.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.api.admin.AdminController;
import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AdminController.class})
@Import(ApiExceptionHandler.class)
class AdminControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParseResultQueryApplicationService parseResultQueryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getParseResultsReturnsList() throws Exception {
        when(parseResultQueryService.listAll()).thenReturn(List.of(
                new ParseResultQueryApplicationService.ParseResultSummary(
                        "ced4c5ef", "sample.pdf", 29, 8,
                        "aws-bda", "2025-03-01",
                        Instant.parse("2026-03-22T10:00:00Z"))));

        mockMvc.perform(get("/admin/parse_results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].index_name").value("ced4c5ef"))
                .andExpect(jsonPath("$[0].filename").value("sample.pdf"))
                .andExpect(jsonPath("$[0].chunk_count").value(29));
    }

    @Test
    void getRawReturns404WhenNotFound() throws Exception {
        when(parseResultQueryService.fetchRawBdaJson(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/parse_results/unknown/raw"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRawReturns502OnS3Failure() throws Exception {
        when(parseResultQueryService.fetchRawBdaJson(anyString()))
                .thenThrow(new ParseResultQueryApplicationService.S3FetchException("S3 error"));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/raw"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getRawReturns502WhenS3ObjectMissing() throws Exception {
        when(parseResultQueryService.fetchRawBdaJson(anyString()))
                .thenThrow(new ParseResultQueryApplicationService.S3ObjectNotFoundException("key not found"));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/raw"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getChunksReturns404WhenIndexNotFound() throws Exception {
        when(parseResultQueryService.fetchIndexedChunks(anyString()))
                .thenThrow(new ParseResultQueryApplicationService.IndexNotFoundException("not found"));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/chunks"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChunksReturnsChunkList() throws Exception {
        when(parseResultQueryService.fetchIndexedChunks("ced4c5ef")).thenReturn(List.of(
                new ParseResultQueryApplicationService.IndexedChunk(
                        "chunk-1", 1, List.of("Executive Summary"),
                        "The client agreement...", "The client agreement.", List.of())));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunk_id").value("chunk-1"))
                .andExpect(jsonPath("$[0].page_number").value(1));
    }
}
