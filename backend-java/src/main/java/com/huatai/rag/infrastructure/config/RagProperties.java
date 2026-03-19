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
}
