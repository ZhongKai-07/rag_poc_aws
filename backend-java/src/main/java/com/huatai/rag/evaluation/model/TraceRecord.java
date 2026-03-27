package com.huatai.rag.evaluation.model;

import com.huatai.rag.domain.rag.StructuredQuery;
import java.util.List;

public record TraceRecord(
        String caseId,
        String module,
        String originalQuery,
        String rewrittenQuery,
        List<String> extractedKeywords,
        StructuredQuery structuredQuery,
        List<String> retrievedContexts,
        String generatedAnswer,
        String referenceAnswer,
        List<ExpectedSource> expectedSources,
        long rewriteLatencyMs,
        long retrievalLatencyMs,
        long generationLatencyMs
) {}
