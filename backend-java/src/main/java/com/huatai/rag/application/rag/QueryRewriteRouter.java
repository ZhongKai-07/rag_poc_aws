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
            return RewriteResult.passthrough(query);
        }

        try {
            QueryRewriteStrategy selected = strategies.stream()
                    .filter(s -> s.supports(module))
                    .findFirst()
                    .orElse(defaultStrategy);
            return selected.rewrite(query);
        } catch (Exception e) {
            log.warn("Query rewrite failed, falling back to original query: {}", e.getMessage());
            return RewriteResult.passthrough(query);
        }
    }
}
