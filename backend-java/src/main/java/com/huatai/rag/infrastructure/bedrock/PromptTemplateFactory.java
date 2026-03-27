package com.huatai.rag.infrastructure.bedrock;

public class PromptTemplateFactory {

    public String buildSystemPrompt() {
        return "你是一个证券公司的COB专家，请根据相关文档回答用户的问题。";
    }

    public String buildUserPrompt(String question) {
        return """
                用户问题如下:
                %s

                不需要前言与解释，直接输出答案。
                """.formatted(question);
    }

    public String buildContextFirstPrompt(String question, String formattedContext) {
        StringBuilder builder = new StringBuilder();
        if (formattedContext != null && !formattedContext.isEmpty()) {
            builder.append(formattedContext).append("\n");
        }
        builder.append(buildUserPrompt(question));
        return builder.toString();
    }
}
