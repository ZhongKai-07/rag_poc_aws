package com.huatai.rag.domain.rag;

import java.util.List;

public record RewriteResult(
        String rewrittenQuery,
        List<String> keywords,
        StructuredQuery structured,
        String originalQuery
) {
    public static RewriteResult passthrough(String originalQuery) {
        return new RewriteResult(originalQuery, List.of(), null, originalQuery);
    }
}
