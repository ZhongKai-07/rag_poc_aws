package com.huatai.rag.application;

import com.huatai.rag.application.rag.CitationAssemblyService;
import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CitationAssemblyServiceTest {

    @Test
    void assemble_numbers_documents_and_extracts_metadata() {
        var docs = List.of(
                new RetrievedDocument("KYC审查流程...", 0.9, null,
                        Map.of("filename", "AML手册.pdf", "page_number", 12,
                                "section_path", List.of("第三章", "KYC审查"))),
                new RetrievedDocument("PI分类标准...", 0.8, null,
                        Map.of("filename", "开户指引.pdf", "page_number", 5,
                                "section_path", List.of("第二章"))));

        var service = new CitationAssemblyService(null);
        var result = service.assemble(docs);

        assertThat(result.formattedContext()).contains("[1] (AML手册.pdf, 第12页)");
        assertThat(result.formattedContext()).contains("[2] (开户指引.pdf, 第5页)");
        assertThat(result.citationMap()).hasSize(2);
        assertThat(result.citationMap().get(1).filename()).isEqualTo("AML手册.pdf");
        assertThat(result.citationMap().get(1).sectionPath()).isEqualTo("第三章/KYC审查");
    }

    @Test
    void assemble_falls_back_to_unknown_when_filename_missing() {
        var docs = List.of(
                new RetrievedDocument("text", 0.9, null, Map.of("page_number", 1)));
        var service = new CitationAssemblyService(null);
        var result = service.assemble(docs);
        assertThat(result.citationMap().get(1).filename()).isEqualTo("未知源文档");
    }

    @Test
    void parseResponse_extracts_cited_references() {
        var service = new CitationAssemblyService(null);
        var citations = Map.of(
                1, new Citation(1, "a.pdf", 1, null, "text1"),
                2, new Citation(2, "b.pdf", 2, null, "text2"),
                3, new Citation(3, "c.pdf", 3, null, "text3"));

        var cited = service.parseResponse("根据[1]和[3]，答案是...", citations);
        assertThat(cited.citations()).hasSize(2);
        assertThat(cited.citations().get(0).index()).isEqualTo(1);
        assertThat(cited.citations().get(1).index()).isEqualTo(3);
    }

    @Test
    void parseResponse_handles_variant_formats() {
        var service = new CitationAssemblyService(null);
        var citations = Map.of(
                1, new Citation(1, "a.pdf", 1, null, "t"),
                2, new Citation(2, "b.pdf", 2, null, "t"));

        // [1][2] adjacent format
        var cited = service.parseResponse("答案[1][2]如下", citations);
        assertThat(cited.citations()).hasSize(2);
    }

    @Test
    void parseResponse_returns_empty_citations_when_none_found() {
        var service = new CitationAssemblyService(null);
        var cited = service.parseResponse("没有引用的答案", Map.of());
        assertThat(cited.citations()).isEmpty();
    }
}
