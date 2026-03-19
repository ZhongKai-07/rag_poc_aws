package com.huatai.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huatai.rag.domain.document.IndexNamingPolicy;
import com.huatai.rag.domain.retrieval.RetrievalResult;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import com.huatai.rag.domain.retrieval.SearchMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    @Test
    void indexNamingPolicyMatchesPythonCompatibilityRule() {
        assertThat(IndexNamingPolicy.indexNameFor("PRC Client.pdf")).isEqualTo("2374dcf7");
        assertThat(IndexNamingPolicy.indexNameFor("(1) OTCD - ISDA & CSA in same doc.pdf")).isEqualTo("2f295fa6");
    }

    @Test
    void searchMethodParsesSupportedFrontendValuesCaseInsensitively() {
        assertThat(SearchMethod.fromValue("vector")).isEqualTo(SearchMethod.VECTOR);
        assertThat(SearchMethod.fromValue("TEXT")).isEqualTo(SearchMethod.TEXT);
        assertThat(SearchMethod.fromValue("Mix")).isEqualTo(SearchMethod.MIX);

        assertThatThrownBy(() -> SearchMethod.fromValue("hybrid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported search method");
    }

    @Test
    void retrievalResultExposesRerankedDocumentsAsSourceDocuments() {
        RetrievedDocument recallDocument = new RetrievedDocument(
                "recall paragraph",
                88.1,
                null,
                Map.of("filename", "recall.pdf", "page", 2));
        RetrievedDocument rerankDocument = new RetrievedDocument(
                "rerank paragraph",
                91.4,
                0.96,
                Map.of("filename", "rerank.pdf", "page", 1));

        RetrievalResult retrievalResult = new RetrievalResult(
                List.of(recallDocument),
                List.of(rerankDocument));

        assertThat(retrievalResult.recallDocuments()).containsExactly(recallDocument);
        assertThat(retrievalResult.rerankDocuments()).containsExactly(rerankDocument);
        assertThat(retrievalResult.sourceDocuments()).containsExactly(rerankDocument);
    }
}
