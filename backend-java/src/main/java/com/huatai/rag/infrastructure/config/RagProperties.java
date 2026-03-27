package com.huatai.rag.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huatai.rag")
public class RagProperties {

    private double vecScoreThreshold = 0.0;
    private double textScoreThreshold = 0.0;
    private double rerankScoreThreshold = 0.5;
    private Duration requestTimeout = Duration.ofSeconds(30);
    private int retryMaxAttempts = 3;
    private Duration retryBackoff = Duration.ofSeconds(1);
    private String embeddingModelId = "amazon.titan-embed-text-v1";
    private String rerankModelId = "cohere.rerank-v3-5:0";
    private String answerModelId = "qwen.qwen3-235b-a22b-2507-v1:0";
    private boolean queryRewriteEnabled = true;
    private boolean citationEnabled = true;
    // amazon.nova-lite-v1:0 is cross-region only in us-west-2, needs inference profile ARN
    // Using haiku which supports on-demand throughput
    private String rewriteModelId = "qwen.qwen3-235b-a22b-2507-v1:0";

    public double getVecScoreThreshold() {
        return vecScoreThreshold;
    }

    public void setVecScoreThreshold(double vecScoreThreshold) {
        this.vecScoreThreshold = vecScoreThreshold;
    }

    public double getTextScoreThreshold() {
        return textScoreThreshold;
    }

    public void setTextScoreThreshold(double textScoreThreshold) {
        this.textScoreThreshold = textScoreThreshold;
    }

    public double getRerankScoreThreshold() {
        return rerankScoreThreshold;
    }

    public void setRerankScoreThreshold(double rerankScoreThreshold) {
        this.rerankScoreThreshold = rerankScoreThreshold;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public String getRerankModelId() {
        return rerankModelId;
    }

    public void setRerankModelId(String rerankModelId) {
        this.rerankModelId = rerankModelId;
    }

    public String getAnswerModelId() {
        return answerModelId;
    }

    public void setAnswerModelId(String answerModelId) {
        this.answerModelId = answerModelId;
    }

    public boolean isQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public void setQueryRewriteEnabled(boolean queryRewriteEnabled) {
        this.queryRewriteEnabled = queryRewriteEnabled;
    }

    public boolean isCitationEnabled() {
        return citationEnabled;
    }

    public void setCitationEnabled(boolean citationEnabled) {
        this.citationEnabled = citationEnabled;
    }

    public String getRewriteModelId() {
        return rewriteModelId;
    }

    public void setRewriteModelId(String rewriteModelId) {
        this.rewriteModelId = rewriteModelId;
    }
}
