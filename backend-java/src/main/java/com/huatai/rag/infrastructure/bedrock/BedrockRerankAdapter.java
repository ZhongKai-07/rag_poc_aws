package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.retrieval.RerankPort;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.infrastructure.config.RagProperties;
import com.huatai.rag.infrastructure.support.RetryUtils;
import java.util.Comparator;
import java.util.List;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

public class BedrockRerankAdapter implements RerankPort {

    @SuppressWarnings("unused")
    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    @SuppressWarnings("unused")
    private final RagProperties ragProperties;
    @SuppressWarnings("unused")
    private final RetryUtils retryUtils;

    public BedrockRerankAdapter(
            BedrockAgentRuntimeClient bedrockAgentRuntimeClient,
            RagProperties ragProperties,
            RetryUtils retryUtils) {
        this.bedrockAgentRuntimeClient = bedrockAgentRuntimeClient;
        this.ragProperties = ragProperties;
        this.retryUtils = retryUtils;
    }

    @Override
    public List<RetrievedDocument> rerank(String query, List<RetrievedDocument> documents, double rerankScoreThreshold) {
        return documents.stream()
                .sorted(Comparator.comparingDouble(RetrievedDocument::score).reversed())
                .map(document -> new RetrievedDocument(
                        document.pageContent(),
                        document.score(),
                        document.rerankScore() != null ? document.rerankScore() : document.score() / 100.0,
                        document.metadata()))
                .filter(document -> document.rerankScore() == null || document.rerankScore() >= rerankScoreThreshold)
                .toList();
    }
}
