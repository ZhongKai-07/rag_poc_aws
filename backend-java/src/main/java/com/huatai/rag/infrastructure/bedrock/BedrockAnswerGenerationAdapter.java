package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

public class BedrockAnswerGenerationAdapter implements AnswerGenerationPort {

    private static final Logger log = LoggerFactory.getLogger(BedrockAnswerGenerationAdapter.class);

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
    public String generateAnswer(String query, String formattedContext) {
        return retryUtils.executeWithRetry(() -> invoke(query, formattedContext),
                ragProperties.getRetryMaxAttempts(),
                ragProperties.getRetryBackoff());
    }

    private String invoke(String query, String formattedContext) {
        String userContent = promptTemplateFactory.buildContextFirstPrompt(query, formattedContext);
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

    @Override
    public void generateAnswerStream(String query, String formattedContext,
                                     Consumer<String> tokenConsumer) {
        // Synchronous client fallback: generate full answer then emit in chunks.
        // Will be upgraded to BedrockRuntimeAsyncClient.converseStream() when
        // async client is added to the project (Spring AI migration or later).
        log.info("[Streaming] generating answer synchronously then chunking for SSE");
        String fullAnswer = generateAnswer(query, formattedContext);
        int chunkSize = 20;
        for (int i = 0; i < fullAnswer.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullAnswer.length());
            tokenConsumer.accept(fullAnswer.substring(i, end));
        }
    }
}
