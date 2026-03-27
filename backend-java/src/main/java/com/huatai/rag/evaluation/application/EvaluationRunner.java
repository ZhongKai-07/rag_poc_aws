package com.huatai.rag.evaluation.application;

import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.evaluation.infrastructure.RagasClient;
import com.huatai.rag.evaluation.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);

    private final RagQueryApplicationService ragQueryService;
    private final RagasClient ragasClient;

    public EvaluationRunner(RagQueryApplicationService ragQueryService, RagasClient ragasClient) {
        this.ragQueryService = ragQueryService;
        this.ragasClient = ragasClient;
    }

    public EvaluationReport run(TestDataset dataset) {
        log.info("Starting evaluation for dataset '{}' with {} cases", dataset.dataset(), dataset.cases().size());

        List<TraceRecord> traces = new ArrayList<>();
        List<EvaluationReport.CaseResult> caseResults = new ArrayList<>();

        for (TestCase testCase : dataset.cases()) {
            TraceRecord trace = executeCase(testCase);
            traces.add(trace);

            Map<String, Double> customMetrics = computeCustomMetrics(testCase, trace);
            caseResults.add(new EvaluationReport.CaseResult(testCase.id(), trace, customMetrics));
        }

        // Call RAGAS for standard metrics
        Map<String, Double> ragasMetrics = ragasClient.evaluate(traces);

        // Merge custom aggregate metrics
        Map<String, Double> aggregateMetrics = new LinkedHashMap<>(ragasMetrics);
        aggregateMetrics.putAll(computeAggregateCustomMetrics(caseResults));

        return new EvaluationReport(dataset.dataset(), dataset.cases().size(), aggregateMetrics, caseResults);
    }

    private TraceRecord executeCase(TestCase testCase) {
        long start = System.currentTimeMillis();

        RagQueryApplicationService.QueryResult result = ragQueryService.handle(
                new RagQueryApplicationService.QueryCommand(
                        "eval-" + testCase.id(),
                        List.of("default"),
                        testCase.query(),
                        testCase.module(),
                        3, 2, 0.0, 0.0, 0.5, "mix"));

        long totalLatency = System.currentTimeMillis() - start;

        List<String> contexts = result.sourceDocuments().stream()
                .map(RetrievedDocument::pageContent)
                .toList();

        return new TraceRecord(
                testCase.id(),
                testCase.module(),
                testCase.query(),
                testCase.query(),
                testCase.expectedKeywords(),
                testCase.expectedStructured(),
                contexts,
                result.answer(),
                testCase.referenceAnswer(),
                testCase.expectedSources(),
                0, totalLatency, 0);
    }

    private Map<String, Double> computeCustomMetrics(TestCase testCase, TraceRecord trace) {
        Map<String, Double> metrics = new LinkedHashMap<>();

        // Keyword Recall
        if (testCase.expectedKeywords() != null && !testCase.expectedKeywords().isEmpty()) {
            long found = testCase.expectedKeywords().stream()
                    .filter(kw -> trace.generatedAnswer().toLowerCase().contains(kw.toLowerCase()))
                    .count();
            metrics.put("keyword_recall", (double) found / testCase.expectedKeywords().size());
        }

        // Citation count
        long citationCount = trace.generatedAnswer().chars()
                .filter(c -> c == '[')
                .count();
        metrics.put("citation_count", (double) citationCount);

        return metrics;
    }

    private Map<String, Double> computeAggregateCustomMetrics(List<EvaluationReport.CaseResult> results) {
        Map<String, Double> aggregate = new LinkedHashMap<>();
        double avgKeywordRecall = results.stream()
                .map(r -> r.metrics().getOrDefault("keyword_recall", 0.0))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        aggregate.put("avg_keyword_recall", avgKeywordRecall);
        return aggregate;
    }
}
