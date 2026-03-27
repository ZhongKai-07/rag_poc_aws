package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollateralStructuredRewriteStrategyTest {

    @Test
    void supports_collateral_module() {
        var strategy = new CollateralStructuredRewriteStrategy(mock(BedrockRuntimeClient.class), new RagProperties());
        assertThat(strategy.supports("collateral")).isTrue();
        assertThat(strategy.supports("COLLATERAL")).isTrue();
        assertThat(strategy.supports("cob")).isFalse();
    }

    @Test
    void parses_full_structured_response() {
        var client = mock(BedrockRuntimeClient.class);
        var json = """
                {"counterparty": "HSBC", "agreement_type": "ISDA_CSA",
                 "business_field": "minimum transfer amount",
                 "fallback_query": "HSBC ISDA MTA"}""";
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText(json))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CollateralStructuredRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("华泰和HSBC的ISDA下MTA是多少");

        assertThat(result.structured()).isNotNull();
        assertThat(result.structured().counterparty()).isEqualTo("HSBC");
        assertThat(result.structured().agreementType()).isEqualTo("ISDA_CSA");
        assertThat(result.structured().businessField()).isEqualTo("minimum transfer amount");
        assertThat(result.rewrittenQuery()).isEqualTo("minimum transfer amount");
    }

    @Test
    void uses_fallback_on_partial_parse() {
        var client = mock(BedrockRuntimeClient.class);
        var json = """
                {"counterparty": null, "agreement_type": null,
                 "business_field": null, "fallback_query": "MTA查询"}""";
        var response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.fromText(json))
                                .build())
                        .build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        var strategy = new CollateralStructuredRewriteStrategy(client, new RagProperties());
        var result = strategy.rewrite("MTA是什么");

        assertThat(result.rewrittenQuery()).isEqualTo("MTA查询");
        assertThat(result.structured().counterparty()).isNull();
    }
}
