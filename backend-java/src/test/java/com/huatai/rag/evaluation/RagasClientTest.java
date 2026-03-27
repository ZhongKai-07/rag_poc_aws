package com.huatai.rag.evaluation;

import com.huatai.rag.evaluation.infrastructure.RagasClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RagasClientTest {

    @Test
    void parses_evaluation_response() {
        var client = new RagasClient("http://localhost:8002");
        var json = """
                {"faithfulness": [0.85], "answer_relevancy": [0.90],
                 "context_precision": [0.75], "context_recall": [0.80]}""";
        var metrics = client.parseMetrics(json);
        assertThat(metrics.get("faithfulness")).isEqualTo(0.85);
        assertThat(metrics.get("answer_relevancy")).isEqualTo(0.90);
        assertThat(metrics.get("context_precision")).isEqualTo(0.75);
        assertThat(metrics.get("context_recall")).isEqualTo(0.80);
    }

    @Test
    void handles_empty_arrays() {
        var client = new RagasClient("http://localhost:8002");
        var json = """
                {"faithfulness": [], "answer_relevancy": []}""";
        var metrics = client.parseMetrics(json);
        assertThat(metrics).isEmpty();
    }
}
