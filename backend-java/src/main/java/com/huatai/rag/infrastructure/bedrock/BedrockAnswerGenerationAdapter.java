package com.huatai.rag.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

public class BedrockAnswerGenerationAdapter implements AnswerGenerationPort {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;
    private final PromptTemplateFactory promptTemplateFactory;
    private final RetryUtils retryUtils;

    public BedrockAnswerGenerationAdapter(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            PromptTemplateFactory promptTemplateFactory,
            RetryUtils retryUtils) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
        this.promptTemplateFactory = promptTemplateFactory;
        this.retryUtils = retryUtils;
    }

    @Override
    public String generateAnswer(String query, List<RetrievedDocument> sourceDocuments) {
        return retryUtils.executeWithRetry(() -> invoke(query, sourceDocuments),
                ragProperties.getRetryMaxAttempts(),
                ragProperties.getRetryBackoff());
    }

    private String invoke(String query, List<RetrievedDocument> sourceDocuments) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "system", promptTemplateFactory.buildSystemPrompt(),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of(
                                    "type", "text",
                                    "text", promptTemplateFactory.buildContextFirstPrompt(query, sourceDocuments))))),
                    "max_tokens", 1024,
                    "temperature", 0.1));

            var response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(ragProperties.getAnswerModelId())
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(payload))
                    .build());

            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText();
            }
            return root.path("completion").asText("");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse answer generation response", exception);
        }
    }
}
