package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BedrockConversationMemoryAdapterTest {

    @Test
    void compress_calls_bedrock_and_returns_summary() {
        var bedrockClient = mock(BedrockRuntimeClient.class);
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText("用户询问了KYC流程，助手解释了审查要点。"))
                                .build())
                        .build())
                .build();
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(response);

        var adapter = new BedrockConversationMemoryAdapter(bedrockClient, new RagProperties(), new RetryUtils());
        var messages = List.of(
                new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), "USER", "KYC流程是什么？", null, null, Instant.now()),
                new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), "ASSISTANT", "KYC审查要点...", null, null, Instant.now())
        );

        String result = adapter.compress(messages);
        assertThat(result).contains("KYC");
    }
}
