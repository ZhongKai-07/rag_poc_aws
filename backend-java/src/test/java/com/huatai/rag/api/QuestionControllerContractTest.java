package com.huatai.rag.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.api.question.QuestionController;
import com.huatai.rag.api.question.dto.TopQuestionsResponse;
import com.huatai.rag.application.history.QuestionHistoryApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileCopyUtils;

@WebMvcTest(controllers = QuestionController.class)
@Import(ApiExceptionHandler.class)
class QuestionControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionHistoryApplicationService questionHistoryApplicationService;

    @Test
    void getTopQuestionsReturnsBaselineFixture() throws Exception {
        when(questionHistoryApplicationService.getTopQuestions("32a592c0", 5)).thenReturn(loadFixture());

        mockMvc.perform(get("/top_questions/32a592c0"))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/top-questions-response.json"), true));
    }

    @Test
    void getTopQuestionsMultiReturnsBaselineFixture() throws Exception {
        when(questionHistoryApplicationService.getTopQuestionsMulti("2f295fa6,32a592c0", 5)).thenReturn(loadFixture());

        mockMvc.perform(get("/top_questions_multi")
                        .param("index_names", "2f295fa6,32a592c0"))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/top-questions-response.json"), true));
    }

    private TopQuestionsResponse loadFixture() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(readFixture("fixtures/contracts/top-questions-response.json"), TopQuestionsResponse.class);
    }

    private String readFixture(String path) throws Exception {
        return new String(FileCopyUtils.copyToByteArray(new ClassPathResource(path).getInputStream()));
    }
}
