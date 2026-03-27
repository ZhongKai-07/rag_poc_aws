package com.huatai.rag.application;

import com.huatai.rag.application.rag.QueryRewriteRouter;
import com.huatai.rag.domain.rag.QueryRewriteStrategy;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryRewriteRouterTest {

    @Test
    void routes_to_matching_strategy() {
        var cobStrategy = mock(QueryRewriteStrategy.class);
        when(cobStrategy.supports("cob")).thenReturn(true);
        when(cobStrategy.rewrite("test")).thenReturn(
                new RewriteResult("rewritten", List.of("KYC"), null, "test"));

        var props = new RagProperties();
        var router = new QueryRewriteRouter(List.of(cobStrategy), cobStrategy, props);

        var result = router.rewrite("test", "cob");
        assertThat(result.rewrittenQuery()).isEqualTo("rewritten");
    }

    @Test
    void falls_back_to_default_for_unknown_module() {
        var defaultStrategy = mock(QueryRewriteStrategy.class);
        when(defaultStrategy.supports(anyString())).thenReturn(false);
        when(defaultStrategy.rewrite("test")).thenReturn(RewriteResult.passthrough("test"));

        var props = new RagProperties();
        var router = new QueryRewriteRouter(List.of(), defaultStrategy, props);

        var result = router.rewrite("test", "RAG");
        assertThat(result.rewrittenQuery()).isEqualTo("test");
    }

    @Test
    void returns_passthrough_when_disabled() {
        var props = new RagProperties();
        props.setQueryRewriteEnabled(false);

        var strategy = mock(QueryRewriteStrategy.class);
        var router = new QueryRewriteRouter(List.of(strategy), strategy, props);

        var result = router.rewrite("test", "cob");
        assertThat(result.rewrittenQuery()).isEqualTo("test");
        assertThat(result.originalQuery()).isEqualTo("test");
        verify(strategy, never()).rewrite(anyString());
    }

    @Test
    void catches_strategy_exception_and_returns_passthrough() {
        var strategy = mock(QueryRewriteStrategy.class);
        when(strategy.supports("cob")).thenReturn(true);
        when(strategy.rewrite(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        var props = new RagProperties();
        var router = new QueryRewriteRouter(List.of(strategy), strategy, props);

        var result = router.rewrite("test", "cob");
        assertThat(result.rewrittenQuery()).isEqualTo("test");
    }
}
