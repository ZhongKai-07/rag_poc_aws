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
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.infrastructure.config.StreamingConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RagController.class)
@Import({ApiExceptionHandler.class, StreamingConfig.class})
class RagControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagQueryApplicationService ragQueryApplicationService;

    @MockBean
    private AnswerGenerationPort answerGenerationPort;

    @Test
    void postRagAnswerReturnsBaselineCompatiblePayload() throws Exception {
        when(ragQueryApplicationService.handle(any(RagQueryApplicationService.QueryCommand.class)))
                .thenReturn(new RagQueryApplicationService.QueryResult(
                        "A baseline-compatible answer is returned as plain text.",
                        List.of(
                                new RetrievedDocument(
                                        "The onboarding flow requires completing the agreement review before account activation.",
                                        91.2,
                                        0.93,
                                        Map.of()),
                                new RetrievedDocument(
                                        "The ISDA and CSA terms are maintained in the same source document for this baseline sample.",
                                        86.4,
                                        0.88,
                                        Map.of())),
                        List.of(
                                new RetrievedDocument(
                                        "The onboarding flow requires completing the agreement review before account activation.",
                                        91.2,
                                        null,
                                        Map.of()),
                                new RetrievedDocument(
                                        "The ISDA and CSA terms are maintained in the same source document for this baseline sample.",
                                        86.4,
                                        null,
                                        Map.of()),
                                new RetrievedDocument(
                                        "Client materials describe the decision checkpoints before processing begins.",
                                        81.7,
                                        null,
                                        Map.of())),
                        List.of(
                                new RetrievedDocument(
                                        "The onboarding flow requires completing the agreement review before account activation.",
                                        91.2,
                                        0.93,
                                        Map.of()),
                                new RetrievedDocument(
                                        "The ISDA and CSA terms are maintained in the same source document for this baseline sample.",
                                        86.4,
                                        0.88,
                                        Map.of())),
                        List.of()));

        mockMvc.perform(post("/rag_answer")
                        .contentType(APPLICATION_JSON)
                        .content(readFixture("fixtures/contracts/rag-answer-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/rag-answer-response.json"), true));
    }

    private String readFixture(String path) throws Exception {
        return new String(FileCopyUtils.copyToByteArray(new ClassPathResource(path).getInputStream()));
    }
}
