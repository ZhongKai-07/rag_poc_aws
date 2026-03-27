package com.huatai.rag.infrastructure.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesFeatureFlagTest {

    @Test
    void queryRewrite_enabled_by_default() {
        var props = new RagProperties();
        assertThat(props.isQueryRewriteEnabled()).isTrue();
    }

    @Test
    void citation_enabled_by_default() {
        var props = new RagProperties();
        assertThat(props.isCitationEnabled()).isTrue();
    }

    @Test
    void rewriteModelId_has_default() {
        var props = new RagProperties();
        assertThat(props.getRewriteModelId()).isNotBlank();
    }
}
