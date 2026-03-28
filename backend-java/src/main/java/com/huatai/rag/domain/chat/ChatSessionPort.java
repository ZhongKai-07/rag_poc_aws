package com.huatai.rag.domain.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionPort {
    ChatSession create(String title, String module);
    Optional<ChatSession> findById(UUID id);
    List<ChatSession> listSessions(int page, int size);
    void updateTitle(UUID id, String title);
    void delete(UUID id);
}
