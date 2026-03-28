package com.huatai.rag.domain.rag;

public enum ConfidenceLevel {
    HIGH, MEDIUM, LOW;

    public static ConfidenceLevel fromScore(Double maxRerankScore) {
        if (maxRerankScore == null) return LOW;
        if (maxRerankScore >= 0.8) return HIGH;
        if (maxRerankScore >= 0.5) return MEDIUM;
        return LOW;
    }
}
