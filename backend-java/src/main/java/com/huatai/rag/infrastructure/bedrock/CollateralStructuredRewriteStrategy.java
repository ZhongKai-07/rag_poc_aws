package com.huatai.rag.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.rag.QueryRewriteStrategy;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.domain.rag.StructuredQuery;
import com.huatai.rag.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CollateralStructuredRewriteStrategy implements QueryRewriteStrategy {

    private static final Logger log = LoggerFactory.getLogger(CollateralStructuredRewriteStrategy.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
            你是一个证券公司Collateral业务专家。请从用户问题中提取以下结构化信息：
            1. counterparty：交易对手方名称（如HSBC、Goldman Sachs）
            2. agreement_type：协议类型（如ISDA、CSA、GMRA、GMSLA）
            3. business_field：查询的具体业务字段（如minimum transfer amount、initial margin、eligible collateral）
            4. fallback_query：如果无法完全结构化，保留原始问题的检索友好改写

            用户问题：%s

            输出JSON格式：
            {"counterparty": "...", "agreement_type": "...", "business_field": "...", "fallback_query": "..."}""";

    private final BedrockRuntimeClient client;
    private final RagProperties ragProperties;

    public CollateralStructuredRewriteStrategy(BedrockRuntimeClient client, RagProperties ragProperties) {
        this.client = client;
        this.ragProperties = ragProperties;
    }

    @Override
    public boolean supports(String module) {
        return "collateral".equalsIgnoreCase(module);
    }

    @Override
    public RewriteResult rewrite(String query) {
        try {
            String responseText = CompletableFuture.supplyAsync(() -> callLlm(query))
                    .orTimeout(3, TimeUnit.SECONDS)
                    .join();
            return parseResponse(responseText, query);
        } catch (Exception e) {
            log.warn("Collateral rewrite failed for query '{}': {}", query, e.getMessage());
            return RewriteResult.passthrough(query);
        }
    }

    private String callLlm(String query) {
        var response = client.converse(ConverseRequest.builder()
                .modelId(ragProperties.getRewriteModelId())
                .system(SystemContentBlock.builder()
                        .text("你是一个结构化查询解析助手，只输出JSON，不要输出其他内容。")
                        .build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(String.format(PROMPT_TEMPLATE, query)))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(256)
                        .temperature(0.0f)
                        .build())
                .build());
        return response.output().message().content().get(0).text();
    }

    private RewriteResult parseResponse(String responseText, String originalQuery) {
        try {
            JsonNode root = MAPPER.readTree(responseText);
            String counterparty = nullableText(root, "counterparty");
            String agreementType = nullableText(root, "agreement_type");
            String businessField = nullableText(root, "business_field");
            String fallbackQuery = root.path("fallback_query").asText(originalQuery);

            var structured = new StructuredQuery(counterparty, agreementType, businessField, fallbackQuery);

            String rewrittenQuery = businessField != null ? businessField : fallbackQuery;

            return new RewriteResult(rewrittenQuery, List.of(), structured, originalQuery);
        } catch (Exception e) {
            log.warn("Failed to parse Collateral rewrite response: {}", e.getMessage());
            return RewriteResult.passthrough(originalQuery);
        }
    }

    private static String nullableText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return text.isEmpty() ? null : text;
    }
}
