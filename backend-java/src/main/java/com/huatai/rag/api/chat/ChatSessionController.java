package com.huatai.rag.api.chat;

import com.huatai.rag.api.chat.dto.CreateSessionRequest;
import com.huatai.rag.api.chat.dto.FeedbackDto;
import com.huatai.rag.api.chat.dto.MessageDto;
import com.huatai.rag.api.chat.dto.RenameSessionRequest;
import com.huatai.rag.api.chat.dto.SessionDetailDto;
import com.huatai.rag.api.chat.dto.SessionDto;
import com.huatai.rag.application.chat.ChatSessionApplicationService;
import com.huatai.rag.application.chat.FeedbackApplicationService;
import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.ChatSession;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatSessionController {

    private final ChatSessionApplicationService sessionService;
    private final FeedbackApplicationService feedbackService;

    public ChatSessionController(ChatSessionApplicationService sessionService,
                                  FeedbackApplicationService feedbackService) {
        this.sessionService = sessionService;
        this.feedbackService = feedbackService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionDto> createSession(@RequestBody CreateSessionRequest request) {
        ChatSession session = sessionService.createSession(
                request.getTitle() != null ? request.getTitle() : "新对话",
                request.getModule());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @GetMapping("/sessions")
    public List<SessionDto> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sessionService.listSessions(page, size).stream().map(this::toDto).toList();
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionDetailDto> getSession(@PathVariable UUID id) {
        return sessionService.findSession(id)
                .map(session -> {
                    List<ChatMessage> messages = sessionService.getSessionMessages(id);
                    List<MessageDto> messageDtos = messages.stream().map(this::toMessageDto).toList();
                    return ResponseEntity.ok(new SessionDetailDto(
                            session.id(), session.title(), session.module(),
                            session.createdAt(), messageDtos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/sessions/{id}")
    public ResponseEntity<Void> renameSession(@PathVariable UUID id,
                                               @RequestBody RenameSessionRequest request) {
        sessionService.renameSession(id, request.getTitle());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions/{sessionId}/messages/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedback(@PathVariable UUID sessionId,
                                                @PathVariable UUID messageId,
                                                @RequestBody FeedbackDto request) {
        feedbackService.submitFeedback(sessionId, messageId,
                request.getRating().toUpperCase(), request.getComment());
        return ResponseEntity.ok().build();
    }

    private SessionDto toDto(ChatSession session) {
        return new SessionDto(session.id(), session.title(), session.module(),
                session.createdAt(), session.updatedAt());
    }

    private MessageDto toMessageDto(ChatMessage message) {
        FeedbackDto fb = sessionService.getFeedbackForMessage(message.id())
                .map(f -> {
                    var dto = new FeedbackDto();
                    dto.setRating(f.rating());
                    return dto;
                }).orElse(null);
        return new MessageDto(message.id(), message.role(), message.content(),
                message.citations(), message.suggestedQuestions(), fb, message.createdAt());
    }
}
