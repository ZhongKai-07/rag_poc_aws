package com.huatai.rag.application.chat;

import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.ChatMessagePort;
import com.huatai.rag.domain.chat.ConversationContext;
import com.huatai.rag.domain.chat.HistoryCompressorPort;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    private final ChatMessagePort chatMessagePort;
    private final HistoryCompressorPort compressorPort;
    private final int windowSize;
    private final int keepRecent;

    public ConversationMemoryService(ChatMessagePort chatMessagePort,
                                     HistoryCompressorPort compressorPort,
                                     int windowSize, int keepRecent) {
        this.chatMessagePort = chatMessagePort;
        this.compressorPort = compressorPort;
        this.windowSize = windowSize;
        this.keepRecent = keepRecent;
    }

    public ConversationMemoryService(ChatMessagePort chatMessagePort,
                                     HistoryCompressorPort compressorPort) {
        this(chatMessagePort, compressorPort, 10, 6);
    }

    public ConversationContext loadContext(UUID sessionId) {
        int count = chatMessagePort.countMessages(sessionId);
        if (count == 0) {
            return new ConversationContext("", false);
        }

        List<ChatMessage> allMessages = chatMessagePort.loadAll(sessionId);

        if (count <= windowSize) {
            return new ConversationContext(formatMessages(allMessages), false);
        }

        List<ChatMessage> olderMessages = allMessages.subList(0, allMessages.size() - keepRecent);
        List<ChatMessage> recentMessages = allMessages.subList(allMessages.size() - keepRecent, allMessages.size());

        log.info("[Memory] compressing {} older messages, keeping {} recent", olderMessages.size(), recentMessages.size());
        String summary = compressorPort.compress(olderMessages);
        String formatted = "[对话摘要]: " + summary + "\n\n" + formatMessages(recentMessages);
        return new ConversationContext(formatted, true);
    }

    private String formatMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> (m.role().equals("USER") ? "用户: " : "助手: ") + m.content())
                .collect(Collectors.joining("\n"));
    }
}
