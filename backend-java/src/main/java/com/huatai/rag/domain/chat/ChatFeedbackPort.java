package com.huatai.rag.domain.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatFeedbackPort {
    void upsert(ChatFeedback feedback);
    Optional<ChatFeedback> findByMessageId(UUID messageId);
    List<ChatFeedback> list(String ratingFilter, int page, int size);
    FeedbackStats stats();
}
