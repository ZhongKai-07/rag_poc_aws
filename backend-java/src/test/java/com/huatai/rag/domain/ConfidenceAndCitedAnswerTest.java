package com.huatai.rag.domain;

import com.huatai.rag.domain.rag.ConfidenceLevel;
import com.huatai.rag.domain.rag.CitedAnswer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceAndCitedAnswerTest {

    @Test
    void confidenceLevel_fromScore() {
        assertThat(ConfidenceLevel.fromScore(0.9)).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(ConfidenceLevel.fromScore(0.8)).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(ConfidenceLevel.fromScore(0.6)).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(ConfidenceLevel.fromScore(0.5)).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(ConfidenceLevel.fromScore(0.3)).isEqualTo(ConfidenceLevel.LOW);
        assertThat(ConfidenceLevel.fromScore(null)).isEqualTo(ConfidenceLevel.LOW);
    }

    @Test
    void citedAnswer_includes_suggestedQuestions() {
        var ca = new CitedAnswer("answer", List.of(), List.of("q1", "q2"));
        assertThat(ca.suggestedQuestions()).containsExactly("q1", "q2");
    }
}
