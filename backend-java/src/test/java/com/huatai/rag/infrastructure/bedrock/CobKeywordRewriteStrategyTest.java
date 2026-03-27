package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CobKeywordRewriteStrategyTest {

    @Test
    void supports_cob_module() {
        var strategy = new CobKeywordRewriteStrategy(mock(BedrockRuntimeClient.class), new RagProperties());
        assertThat(strategy.supports("cob")).isTrue();
        assertThat(strategy.supports("COB")).isTrue();
        assertThat(strategy.supports("RAG")).isTrue();
        assertThat(strategy.supports("collateral")).isFalse();
    }

    @Test
    void parses_valid_json_response() {
        var client = mock(BedrockRuntimeClient.class);
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText(
                                        "{\"rewritten_query\": \"AML KYC审查流程\", \"keywords\": [\"AML\", \"KYC\"]}"))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CobKeywordRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("AML审查怎么做");

        assertThat(result.rewrittenQuery()).isEqualTo("AML KYC审查流程");
        assertThat(result.keywords()).containsExactly("AML", "KYC");
        assertThat(result.originalQuery()).isEqualTo("AML审查怎么做");
    }

    @Test
    void falls_back_on_malformed_json() {
        var client = mock(BedrockRuntimeClient.class);
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText("not valid json"))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CobKeywordRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("test query");

        assertThat(result.rewrittenQuery()).isEqualTo("test query");
        assertThat(result.keywords()).isEmpty();
    }
}
