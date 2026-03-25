package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

public class BedrockAnswerGenerationAdapter implements AnswerGenerationPort {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final RagProperties ragProperties;
    private final PromptTemplateFactory promptTemplateFactory;
    private final RetryUtils retryUtils;

    public BedrockAnswerGenerationAdapter(
            BedrockRuntimeClient bedrockRuntimeClient,
            RagProperties ragProperties,
            PromptTemplateFactory promptTemplateFactory,
            RetryUtils retryUtils) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
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
        String userContent = promptTemplateFactory.buildContextFirstPrompt(query, sourceDocuments);
        String systemPrompt = promptTemplateFactory.buildSystemPrompt();

        var response = bedrockRuntimeClient.converse(ConverseRequest.builder()
                .modelId(ragProperties.getAnswerModelId())
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userContent))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(1024)
                        .temperature(0.1f)
                        .build())
                .build());

        return response.output().message().content().get(0).text();
    }
}
