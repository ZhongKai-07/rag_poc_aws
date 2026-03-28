package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.HistoryCompressorPort;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

public class BedrockConversationMemoryAdapter implements HistoryCompressorPort {

    private static final Logger log = LoggerFactory.getLogger(BedrockConversationMemoryAdapter.class);

    private static final String COMPRESSION_PROMPT =
            "请将以下对话历史压缩为一段简洁的摘要，保留关键信息（问题、答案要点、文档来源）：\n\n%s\n\n输出摘要（不超过200字）：";

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final RagProperties ragProperties;
    private final RetryUtils retryUtils;

    public BedrockConversationMemoryAdapter(BedrockRuntimeClient bedrockRuntimeClient,
                                            RagProperties ragProperties,
                                            RetryUtils retryUtils) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.ragProperties = ragProperties;
        this.retryUtils = retryUtils;
    }

    @Override
    public String compress(List<ChatMessage> messages) {
        String history = messages.stream()
                .map(m -> (m.role().equals("USER") ? "用户: " : "助手: ") + m.content())
                .collect(Collectors.joining("\n"));

        log.info("[Memory] compressing {} messages ({} chars)", messages.size(), history.length());

        return retryUtils.executeWithRetry(() -> invoke(history),
                ragProperties.getRetryMaxAttempts(),
                ragProperties.getRetryBackoff());
    }

    private String invoke(String history) {
        String prompt = String.format(COMPRESSION_PROMPT, history);

        var response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(ragProperties.getAnswerModelId())
                .system(SystemContentBlock.builder()
                        .text("你是一个对话摘要助手，请简洁地总结对话内容。")
                        .build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(prompt))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(256)
                        .temperature(0.1f)
                        .build())
                .build());

        return response.output().message().content().get(0).text();
    }
}
