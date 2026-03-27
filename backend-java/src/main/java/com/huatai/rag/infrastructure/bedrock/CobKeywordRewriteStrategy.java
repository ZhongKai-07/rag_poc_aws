package com.huatai.rag.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.rag.QueryRewriteStrategy;
import com.huatai.rag.domain.rag.RewriteResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CobKeywordRewriteStrategy implements QueryRewriteStrategy {

    private static final Logger log = LoggerFactory.getLogger(CobKeywordRewriteStrategy.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
            你是一个证券公司合规领域的查询理解专家。请分析用户问题，完成两个任务：
            1. 提取核心业务领域关键词（如AML、KYC、World-Check、name screening、PI classification等）
            2. 将问题改写为适合文档检索的形式（去口语化、补全缩写、明确指代）

            用户问题：%s

            输出JSON格式：
            {"rewritten_query": "...", "keywords": ["...", "..."]}""";

    private final BedrockRuntimeClient client;
    private final RagProperties ragProperties;

    public CobKeywordRewriteStrategy(BedrockRuntimeClient client, RagProperties ragProperties) {
        this.client = client;
        this.ragProperties = ragProperties;
    }

    @Override
    public boolean supports(String module) {
        String lower = module.toLowerCase();
        return "cob".equals(lower) || "rag".equals(lower);
    }

    @Override
    public RewriteResult rewrite(String query) {
        log.info("[COB] rewriting query: '{}'", query);
        try {
            long start = System.currentTimeMillis();
            String responseText = CompletableFuture.supplyAsync(() -> callLlm(query))
                    .orTimeout(3, TimeUnit.SECONDS)
                    .join();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[COB] LLM response in {}ms: '{}'", elapsed, responseText);
            return parseResponse(responseText, query);
        } catch (Exception e) {
            log.warn("[COB] rewrite failed for query '{}': {}", query, e.getMessage());
            return RewriteResult.passthrough(query);
        }
    }

    private String callLlm(String query) {
        log.debug("[COB] calling Bedrock model={}", ragProperties.getRewriteModelId());
        var response = client.converse(ConverseRequest.builder()
                .modelId(ragProperties.getRewriteModelId())
                .system(SystemContentBlock.builder()
                        .text("你是一个查询改写助手，只输出JSON，不要输出其他内容。")
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
            String rewrittenQuery = root.path("rewritten_query").asText(originalQuery);
            List<String> keywords = new ArrayList<>();
            JsonNode keywordsNode = root.path("keywords");
            if (keywordsNode.isArray()) {
                for (JsonNode kw : keywordsNode) {
                    keywords.add(kw.asText());
                }
            }
            return new RewriteResult(rewrittenQuery, keywords, null, originalQuery);
        } catch (Exception e) {
            log.warn("Failed to parse COB rewrite response: {}", e.getMessage());
            return RewriteResult.passthrough(originalQuery);
        }
    }
}
