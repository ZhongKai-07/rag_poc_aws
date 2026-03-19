package com.huatai.rag.infrastructure.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IndexNamingPolicyTest {

    @Test
    void compatibleIndexNameUsesPythonMd5PrefixRule() {
        assertThat(OpenSearchIndexManager.compatibleIndexName("PRC Client.pdf")).isEqualTo("2374dcf7");
        assertThat(OpenSearchIndexManager.compatibleIndexName("(1) OTCD - ISDA & CSA in same doc.pdf"))
                .isEqualTo("2f295fa6");
    }
}
