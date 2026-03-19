package com.huatai.rag.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.api.rag.RagController;
import com.huatai.rag.api.rag.dto.RagRequest;
import com.huatai.rag.api.rag.dto.RagResponse;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RagController.class)
@Import(ApiExceptionHandler.class)
class RagControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagQueryApplicationService ragQueryApplicationService;

    @Test
    void postRagAnswerReturnsBaselineCompatiblePayload() throws Exception {
        when(ragQueryApplicationService.answer(any(RagRequest.class))).thenReturn(loadResponseFixture());

        mockMvc.perform(post("/rag_answer")
                        .contentType(APPLICATION_JSON)
                        .content(readFixture("fixtures/contracts/rag-answer-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/rag-answer-response.json"), true));
    }

    private RagResponse loadResponseFixture() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(readFixture("fixtures/contracts/rag-answer-response.json"), RagResponse.class);
    }

    private String readFixture(String path) throws Exception {
        return new String(FileCopyUtils.copyToByteArray(new ClassPathResource(path).getInputStream()));
    }
}
