package com.huatai.rag.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.retrieval.EmbeddingPort;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

public class BedrockEmbeddingAdapter implements EmbeddingPort {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;
    private final RetryUtils retryUtils;

    public BedrockEmbeddingAdapter(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            RetryUtils retryUtils) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
        this.retryUtils = retryUtils;
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        return texts.stream().map(this::embedSingle).toList();
    }

    private List<Float> embedSingle(String text) {
        return retryUtils.executeWithRetry(() -> {
            try {
                String payload = objectMapper.writeValueAsString(Map.of("inputText", text));
                var response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                        .modelId(ragProperties.getEmbeddingModelId())
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(payload))
                        .build());
                JsonNode root = objectMapper.readTree(response.body().asUtf8String());
                return objectMapper.convertValue(root.get("embedding"), new com.fasterxml.jackson.core.type.TypeReference<List<Float>>() {
                });
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to parse embedding response", exception);
            }
        }, ragProperties.getRetryMaxAttempts(), ragProperties.getRetryBackoff());
    }
}
