package com.huatai.rag.domain.chat;

import java.util.List;
import java.util.UUID;

public interface ChatMessagePort {
    void save(ChatMessage message);
    List<ChatMessage> loadRecent(UUID sessionId, int limit);
    int countMessages(UUID sessionId);
    List<ChatMessage> loadAll(UUID sessionId);
}
