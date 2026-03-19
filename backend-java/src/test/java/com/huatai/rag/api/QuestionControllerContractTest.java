package com.huatai.rag.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.api.question.QuestionController;
import com.huatai.rag.application.history.QuestionHistoryApplicationService;
import java.util.List;
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
        when(questionHistoryApplicationService.getTopQuestionsView("32a592c0", 5)).thenReturn(loadFixture());

        mockMvc.perform(get("/top_questions/32a592c0"))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/top-questions-response.json"), true));
    }

    @Test
    void getTopQuestionsMultiReturnsBaselineFixture() throws Exception {
        when(questionHistoryApplicationService.getTopQuestionsView(List.of("2f295fa6", "32a592c0"), 5)).thenReturn(loadFixture());

        mockMvc.perform(get("/top_questions_multi")
                        .param("index_names", "2f295fa6,32a592c0"))
                .andExpect(status().isOk())
                .andExpect(content().json(readFixture("fixtures/contracts/top-questions-response.json"), true));
    }

    private QuestionHistoryApplicationService.TopQuestionsResult loadFixture() {
        return new QuestionHistoryApplicationService.TopQuestionsResult(
                "success",
                List.of(
                        new QuestionHistoryApplicationService.QuestionCountResult("What is the onboarding decision path?", 4),
                        new QuestionHistoryApplicationService.QuestionCountResult("What are the ISDA and CSA requirements?", 3),
                        new QuestionHistoryApplicationService.QuestionCountResult("Which client checks must be completed first?", 2)));
    }

    private String readFixture(String path) throws Exception {
        return new String(FileCopyUtils.copyToByteArray(new ClassPathResource(path).getInputStream()));
    }
}
