package com.huatai.rag.domain.chat;

import java.util.List;

public interface HistoryCompressorPort {
    String compress(List<ChatMessage> messages);
}
