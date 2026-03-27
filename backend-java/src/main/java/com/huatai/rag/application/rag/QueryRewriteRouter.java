package com.huatai.rag.application.rag;

import com.huatai.rag.domain.rag.QueryRewriteStrategy;
import com.huatai.rag.domain.rag.RewriteResult;
import com.huatai.rag.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class QueryRewriteRouter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteRouter.class);

    private final List<QueryRewriteStrategy> strategies;
    private final QueryRewriteStrategy defaultStrategy;
    private final RagProperties ragProperties;

    public QueryRewriteRouter(List<QueryRewriteStrategy> strategies,
                              QueryRewriteStrategy defaultStrategy,
                              RagProperties ragProperties) {
        this.strategies = strategies;
        this.defaultStrategy = defaultStrategy;
        this.ragProperties = ragProperties;
    }

    public RewriteResult rewrite(String query, String module) {
        if (!ragProperties.isQueryRewriteEnabled()) {
            log.info("[Rewrite] disabled by config, passthrough for module={}", module);
            return RewriteResult.passthrough(query);
        }

        try {
            QueryRewriteStrategy selected = strategies.stream()
                    .filter(s -> s.supports(module))
                    .findFirst()
                    .orElse(defaultStrategy);
            log.info("[Rewrite] module='{}' -> strategy={}", module, selected.getClass().getSimpleName());
            RewriteResult result = selected.rewrite(query);
            log.info("[Rewrite] '{}' -> '{}', keywords={}", query, result.rewrittenQuery(), result.keywords());
            return result;
        } catch (Exception e) {
            log.warn("[Rewrite] failed for module='{}', falling back to original: {}", module, e.getMessage());
            return RewriteResult.passthrough(query);
        }
    }
}
