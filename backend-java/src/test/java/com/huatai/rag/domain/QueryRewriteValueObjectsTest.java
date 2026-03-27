package com.huatai.rag.domain;

import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.domain.rag.StructuredQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteValueObjectsTest {

    @Test
    void rewriteResult_holds_all_fields() {
        var result = new RewriteResult("rewritten", List.of("AML", "KYC"), null, "original");
        assertThat(result.rewrittenQuery()).isEqualTo("rewritten");
        assertThat(result.keywords()).containsExactly("AML", "KYC");
        assertThat(result.structured()).isNull();
        assertThat(result.originalQuery()).isEqualTo("original");
    }

    @Test
    void structuredQuery_holds_all_fields() {
        var sq = new StructuredQuery("HSBC", "ISDA_CSA", "minimum transfer amount", "fallback");
        assertThat(sq.counterparty()).isEqualTo("HSBC");
        assertThat(sq.agreementType()).isEqualTo("ISDA_CSA");
        assertThat(sq.businessField()).isEqualTo("minimum transfer amount");
        assertThat(sq.fallbackQuery()).isEqualTo("fallback");
    }

    @Test
    void rewriteResult_passthrough_factory() {
        var result = RewriteResult.passthrough("raw query");
        assertThat(result.rewrittenQuery()).isEqualTo("raw query");
        assertThat(result.keywords()).isEmpty();
        assertThat(result.structured()).isNull();
        assertThat(result.originalQuery()).isEqualTo("raw query");
    }
}
