package com.huatai.rag.domain;

import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.rag.CitedAnswer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CitationValueObjectsTest {

    @Test
    void citation_holds_all_fields() {
        var c = new Citation(1, "AML手册.pdf", 12, "第三章/KYC审查", "KYC审查流程...");
        assertThat(c.index()).isEqualTo(1);
        assertThat(c.filename()).isEqualTo("AML手册.pdf");
        assertThat(c.pageNumber()).isEqualTo(12);
        assertThat(c.sectionPath()).isEqualTo("第三章/KYC审查");
        assertThat(c.excerpt()).isEqualTo("KYC审查流程...");
    }

    @Test
    void citation_handles_null_optional_fields() {
        var c = new Citation(1, "未知源文档", null, null, "some text");
        assertThat(c.pageNumber()).isNull();
        assertThat(c.sectionPath()).isNull();
    }

    @Test
    void citedAnswer_holds_answer_and_citations() {
        var citations = List.of(new Citation(1, "f.pdf", 1, null, "text"));
        var ca = new CitedAnswer("根据[1]...", citations, List.of());
        assertThat(ca.answer()).isEqualTo("根据[1]...");
        assertThat(ca.citations()).hasSize(1);
    }
}
