package com.huatai.rag.infrastructure.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockAdapterWiringTest {

    @Test
    void adaptersCanBeConstructedWithBedrockClientsAndSharedHelpers() {
        BedrockRuntimeClient bedrockRuntimeClient = Mockito.mock(BedrockRuntimeClient.class);
        BedrockAgentRuntimeClient bedrockAgentRuntimeClient = Mockito.mock(BedrockAgentRuntimeClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RagProperties ragProperties = new RagProperties();
        ragProperties.setRetryMaxAttempts(3);
        ragProperties.setRetryBackoff(Duration.ofMillis(10));
        RetryUtils retryUtils = new RetryUtils();
        PromptTemplateFactory promptTemplateFactory = new PromptTemplateFactory();

        BedrockEmbeddingAdapter embeddingAdapter =
                new BedrockEmbeddingAdapter(bedrockRuntimeClient, objectMapper, ragProperties, retryUtils);
        BedrockRerankAdapter rerankAdapter =
                new BedrockRerankAdapter(bedrockAgentRuntimeClient, ragProperties, retryUtils);
        BedrockAnswerGenerationAdapter answerAdapter =
                new BedrockAnswerGenerationAdapter(
                        bedrockRuntimeClient,
                        objectMapper,
                        ragProperties,
                        promptTemplateFactory,
                        retryUtils);

        assertThat(embeddingAdapter).isNotNull();
        assertThat(rerankAdapter).isNotNull();
        assertThat(answerAdapter).isNotNull();
    }
}
