package com.huatai.rag.evaluation.model;

import java.util.List;
import java.util.Map;

public record EvaluationReport(
        String datasetName,
        int totalCases,
        Map<String, Double> aggregateMetrics,
        List<CaseResult> caseResults
) {
    public record CaseResult(String caseId, TraceRecord trace, Map<String, Double> metrics) {}
}
