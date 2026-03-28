package com.huatai.rag.application;

import com.huatai.rag.application.rag.CitationAssemblyService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CitationAssemblyServiceBatchBTest {

    private final CitationAssemblyService service = new CitationAssemblyService(null);

    @Test
    void parseLlmOutput_extracts_answer_and_questions_from_json() {
        String json = """
                {"answer": "根据[1]，KYC审查流程...", "suggested_questions": ["q1?", "q2?"]}""";
        var parsed = service.parseLlmOutput(json);
        assertThat(parsed.answerText()).isEqualTo("根据[1]，KYC审查流程...");
        assertThat(parsed.suggestedQuestions()).containsExactly("q1?", "q2?");
    }

    @Test
    void parseLlmOutput_falls_back_on_invalid_json() {
        var parsed = service.parseLlmOutput("plain text answer without json");
        assertThat(parsed.answerText()).isEqualTo("plain text answer without json");
        assertThat(parsed.suggestedQuestions()).isEmpty();
    }

    @Test
    void parseLlmOutput_handles_json_wrapped_in_markdown_codeblock() {
        String wrapped = "```json\n{\"answer\": \"text\", \"suggested_questions\": [\"q\"]}\n```";
        var parsed = service.parseLlmOutput(wrapped);
        assertThat(parsed.answerText()).isEqualTo("text");
        assertThat(parsed.suggestedQuestions()).containsExactly("q");
    }

    @Test
    void cleanExcerpt_removes_ocr_noise() {
        String noisy = "HUATAI FINANCIAL \u0414\u041B \u041D\u0406\u0422 ** <<<<<< text content";
        String cleaned = service.cleanExcerpt(noisy);
        assertThat(cleaned).doesNotContain("\u0414\u041B").doesNotContain("<<<<<<");
        assertThat(cleaned).contains("HUATAI FINANCIAL").contains("text content");
    }

    @Test
    void cleanExcerpt_truncates_at_150_chars() {
        String longText = "A".repeat(200);
        String cleaned = service.cleanExcerpt(longText);
        assertThat(cleaned).hasSize(153); // 150 + "..."
        assertThat(cleaned).endsWith("...");
    }

    @Test
    void parseLlmOutput_handles_empty_input() {
        var parsed = service.parseLlmOutput("");
        assertThat(parsed.answerText()).isEmpty();
        assertThat(parsed.suggestedQuestions()).isEmpty();
    }
}
