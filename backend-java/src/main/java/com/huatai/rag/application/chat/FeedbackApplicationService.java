package com.huatai.rag.application.chat;

import com.huatai.rag.domain.chat.ChatFeedback;
import com.huatai.rag.domain.chat.ChatFeedbackPort;
import com.huatai.rag.domain.chat.FeedbackStats;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeedbackApplicationService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackApplicationService.class);

    private final ChatFeedbackPort chatFeedbackPort;

    public FeedbackApplicationService(ChatFeedbackPort chatFeedbackPort) {
        this.chatFeedbackPort = chatFeedbackPort;
    }

    public void submitFeedback(UUID sessionId, UUID messageId, String rating, String comment) {
        log.info("[Feedback] session={} message={} rating={}", sessionId, messageId, rating);
        var feedback = new ChatFeedback(UUID.randomUUID(), messageId, sessionId,
                rating, comment, Instant.now());
        chatFeedbackPort.upsert(feedback);
    }

    public List<ChatFeedback> listFeedback(String ratingFilter, int page, int size) {
        return chatFeedbackPort.list(ratingFilter, page, size);
    }

    public FeedbackStats getStats() {
        return chatFeedbackPort.stats();
    }
}
