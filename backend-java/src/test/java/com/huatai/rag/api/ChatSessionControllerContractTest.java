package com.huatai.rag.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.huatai.rag.api.chat.ChatSessionController;
import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.application.chat.ChatSessionApplicationService;
import com.huatai.rag.application.chat.FeedbackApplicationService;
import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatSessionController.class)
@Import(ApiExceptionHandler.class)
class ChatSessionControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatSessionApplicationService sessionService;

    @MockBean
    private FeedbackApplicationService feedbackService;

    @Test
    void createSession_returns201() throws Exception {
        var session = new ChatSession(UUID.randomUUID(), "Test", "RAG", Instant.now(), Instant.now());
        when(sessionService.createSession(anyString(), anyString())).thenReturn(session);

        mockMvc.perform(post("/sessions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\": \"Test\", \"module\": \"RAG\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test"));
    }

    @Test
    void listSessions_returns200() throws Exception {
        var session = new ChatSession(UUID.randomUUID(), "Test", "RAG", Instant.now(), Instant.now());
        when(sessionService.listSessions(anyInt(), anyInt())).thenReturn(List.of(session));

        mockMvc.perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test"));
    }

    @Test
    void getSession_returns200_with_messages() throws Exception {
        var id = UUID.randomUUID();
        var session = new ChatSession(id, "Test", "RAG", Instant.now(), Instant.now());
        when(sessionService.findSession(id)).thenReturn(Optional.of(session));
        when(sessionService.getSessionMessages(id)).thenReturn(List.of(
                new ChatMessage(UUID.randomUUID(), id, "USER", "hello", null, null, Instant.now())
        ));

        mockMvc.perform(get("/sessions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test"))
                .andExpect(jsonPath("$.messages[0].role").value("USER"));
    }

    @Test
    void getSession_returns404_when_not_found() throws Exception {
        when(sessionService.findSession(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/sessions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSession_returns204() throws Exception {
        mockMvc.perform(delete("/sessions/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void renameSession_returns200() throws Exception {
        mockMvc.perform(patch("/sessions/" + UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\": \"New Title\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void submitFeedback_returns200() throws Exception {
        mockMvc.perform(post("/sessions/" + UUID.randomUUID() + "/messages/" + UUID.randomUUID() + "/feedback")
                        .contentType(APPLICATION_JSON)
                        .content("{\"rating\": \"thumbs_up\"}"))
                .andExpect(status().isOk());
    }
}
