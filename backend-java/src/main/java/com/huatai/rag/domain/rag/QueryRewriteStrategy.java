package com.huatai.rag.domain.rag;

public interface QueryRewriteStrategy {
    boolean supports(String module);
    RewriteResult rewrite(String query);
}
