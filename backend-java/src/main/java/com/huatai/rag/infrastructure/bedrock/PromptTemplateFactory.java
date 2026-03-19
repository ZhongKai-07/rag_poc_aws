package com.huatai.rag.infrastructure.bedrock;

import com.huatai.rag.domain.retrieval.RetrievedDocument;
import java.util.List;

public class PromptTemplateFactory {

    public String buildSystemPrompt() {
        return "你是一个证券专家，请根据相关文档回答用户的问题。";
    }

    public String buildUserPrompt(String question) {
        return """
                用户问题如下:
                %s

                不需要前言与解释，直接输出答案。
                """.formatted(question);
    }

    public String buildContextFirstPrompt(String question, List<RetrievedDocument> sourceDocuments) {
        StringBuilder builder = new StringBuilder();
        if (!sourceDocuments.isEmpty()) {
            builder.append("相关文档如下:\n");
            for (RetrievedDocument document : sourceDocuments) {
                builder.append(document.pageContent()).append("\n");
            }
            builder.append("\n");
        }
        builder.append(buildUserPrompt(question));
        return builder.toString();
    }
}
