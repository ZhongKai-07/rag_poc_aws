package com.huatai.rag.application.chat;

import com.huatai.rag.domain.chat.ChatFeedback;
import com.huatai.rag.domain.chat.ChatFeedbackPort;
import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.ChatMessagePort;
import com.huatai.rag.domain.chat.ChatSession;
import com.huatai.rag.domain.chat.ChatSessionPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatSessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionApplicationService.class);

    private final ChatSessionPort chatSessionPort;
    private final ChatMessagePort chatMessagePort;
    private final ChatFeedbackPort chatFeedbackPort;

    public ChatSessionApplicationService(ChatSessionPort chatSessionPort,
                                          ChatMessagePort chatMessagePort,
                                          ChatFeedbackPort chatFeedbackPort) {
        this.chatSessionPort = chatSessionPort;
        this.chatMessagePort = chatMessagePort;
        this.chatFeedbackPort = chatFeedbackPort;
    }

    public ChatSession createSession(String title, String module) {
        String trimmedTitle = title.length() > 50 ? title.substring(0, 50).trim() : title;
        log.info("[Session] creating session title='{}' module='{}'", trimmedTitle, module);
        return chatSessionPort.create(trimmedTitle, module);
    }

    public Optional<ChatSession> findSession(UUID id) {
        return chatSessionPort.findById(id);
    }

    public List<ChatSession> listSessions(int page, int size) {
        return chatSessionPort.listSessions(page, size);
    }

    public void renameSession(UUID id, String newTitle) {
        chatSessionPort.updateTitle(id, newTitle);
    }

    public void deleteSession(UUID id) {
        chatSessionPort.delete(id);
    }

    public List<ChatMessage> getSessionMessages(UUID sessionId) {
        return chatMessagePort.loadAll(sessionId);
    }

    public Optional<ChatFeedback> getFeedbackForMessage(UUID messageId) {
        return chatFeedbackPort.findByMessageId(messageId);
    }
}
