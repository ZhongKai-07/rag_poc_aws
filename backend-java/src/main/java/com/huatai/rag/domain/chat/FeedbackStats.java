package com.huatai.rag.domain.chat;

public record FeedbackStats(long total, long thumbsUp, long thumbsDown,
                            double approvalRate) {}
