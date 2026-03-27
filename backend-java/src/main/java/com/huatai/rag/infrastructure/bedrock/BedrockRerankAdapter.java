package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.infrastructure.config.AwsProperties;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingModelConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocumentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSource;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSourceType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankTextDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfigurationType;

public class BedrockRerankAdapter implements RerankPort {

    private static final Logger log = LoggerFactory.getLogger(BedrockRerankAdapter.class);

    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    private final RagProperties ragProperties;
    private final AwsProperties awsProperties;
    private final RetryUtils retryUtils;

    public BedrockRerankAdapter(
            BedrockAgentRuntimeClient bedrockAgentRuntimeClient,
            RagProperties ragProperties,
            AwsProperties awsProperties,
            RetryUtils retryUtils) {
        this.bedrockAgentRuntimeClient = bedrockAgentRuntimeClient;
        this.ragProperties = ragProperties;
        this.awsProperties = awsProperties;
        this.retryUtils = retryUtils;
    }

    @Override
    public List<RetrievedDocument> rerank(String query, List<RetrievedDocument> documents, double rerankScoreThreshold) {
        if (documents.isEmpty()) {
            return List.of();
        }

        return retryUtils.executeWithRetry(
                () -> invokeRerank(query, documents, rerankScoreThreshold),
                ragProperties.getRetryMaxAttempts(),
                ragProperties.getRetryBackoff());
    }

    private List<RetrievedDocument> invokeRerank(String query, List<RetrievedDocument> documents, double rerankScoreThreshold) {
        String rerankRegion = awsProperties.getRegion();
        String modelArn = String.format("arn:aws:bedrock:%s::foundation-model/%s",
                rerankRegion, ragProperties.getRerankModelId());

        List<RerankSource> sources = new ArrayList<>();
        for (RetrievedDocument doc : documents) {
            sources.add(RerankSource.builder()
                    .type(RerankSourceType.INLINE)
                    .inlineDocumentSource(RerankDocument.builder()
                            .type(RerankDocumentType.TEXT)
                            .textDocument(RerankTextDocument.builder()
                                    .text(doc.pageContent())
                                    .build())
                            .build())
                    .build());
        }

        long apiStart = System.currentTimeMillis();
        RerankResponse response = bedrockAgentRuntimeClient.rerank(RerankRequest.builder()
                .queries(RerankQuery.builder()
                        .type("TEXT")
                        .textQuery(q -> q.text(query))
                        .build())
                .sources(sources)
                .rerankingConfiguration(RerankingConfiguration.builder()
                        .type(RerankingConfigurationType.BEDROCK_RERANKING_MODEL)
                        .bedrockRerankingConfiguration(BedrockRerankingConfiguration.builder()
                                .numberOfResults(documents.size())
                                .modelConfiguration(BedrockRerankingModelConfiguration.builder()
                                        .modelArn(modelArn)
                                        .build())
                                .build())
                        .build())
                .build());
        long apiMs = System.currentTimeMillis() - apiStart;

        List<RetrievedDocument> reranked = new ArrayList<>();
        for (RerankResult result : response.results()) {
            int index = result.index();
            double relevanceScore = result.relevanceScore();
            log.debug("[Rerank] doc[{}] score={} (threshold={})", index, relevanceScore, rerankScoreThreshold);
            if (relevanceScore >= rerankScoreThreshold) {
                RetrievedDocument original = documents.get(index);
                reranked.add(new RetrievedDocument(
                        original.pageContent(),
                        original.score(),
                        relevanceScore,
                        original.metadata()));
            }
        }

        log.info("[Rerank] {}ms, {} input docs, {} passed threshold {}", apiMs, documents.size(), reranked.size(), rerankScoreThreshold);
        return reranked;
    }
}
