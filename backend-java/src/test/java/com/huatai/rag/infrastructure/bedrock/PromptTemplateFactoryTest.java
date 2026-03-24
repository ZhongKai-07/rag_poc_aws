package com.huatai.rag.infrastructure.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.domain.retrieval.RetrievedDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateFactoryTest {

    private final PromptTemplateFactory promptTemplateFactory = new PromptTemplateFactory();

    @Test
    void systemPromptKeepsExpertRoleAndDirectAnswerStyle() {
        String systemPrompt = promptTemplateFactory.buildSystemPrompt();

        assertThat(systemPrompt).contains("COB专家");
        assertThat(systemPrompt).contains("根据相关文档回答");
    }

    @Test
    void userPromptPlacesContextBeforeQuestion() {
        String prompt = promptTemplateFactory.buildContextFirstPrompt(
                "开户流程是什么？",
                List.of(new RetrievedDocument(
                        "相关文档段落",
                        92.1,
                        0.95,
                        Map.of("source", "Onboarding Decision Chart.pdf"))));

        assertThat(prompt).contains("相关文档如下");
        assertThat(prompt).contains("相关文档段落");
        assertThat(prompt).contains("用户问题如下");
        assertThat(prompt).contains("开户流程是什么？");
        assertThat(prompt).contains("直接输出答案");
        assertThat(prompt.indexOf("相关文档如下")).isLessThan(prompt.indexOf("用户问题如下"));
    }
}
