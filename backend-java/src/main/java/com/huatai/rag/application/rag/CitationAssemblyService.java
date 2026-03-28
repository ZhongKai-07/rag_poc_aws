package com.huatai.rag.application.rag;

import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.rag.CitedAnswer;
import com.huatai.rag.domain.retrieval.RetrievedDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CitationAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(CitationAssemblyService.class);

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    private static final String CITATION_INSTRUCTION = """

            请在回答中使用引用标记 [n] 标注信息来源。例如：
            用户问题：KYC审查流程是什么？
            回答：根据[1]，KYC审查流程包括客户身份核实和风险评级[2]。

            以下是参考文档：
            """;

    public PromptWithCitations assemble(List<RetrievedDocument> documents) {
        Map<Integer, Citation> citationMap = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append(CITATION_INSTRUCTION);

        for (int i = 0; i < documents.size(); i++) {
            int index = i + 1;
            RetrievedDocument doc = documents.get(i);
            Map<String, Object> metadata = doc.metadata();

            String filename = extractString(metadata, "filename", "未知源文档");
            Integer pageNumber = extractInteger(metadata, "page_number");
            String sectionPath = extractSectionPath(metadata);
            String excerpt = doc.pageContent();

            citationMap.put(index, new Citation(index, filename, pageNumber, sectionPath, excerpt));

            sb.append("\n[").append(index).append("] ");
            sb.append("(").append(filename);
            if (pageNumber != null) {
                sb.append(", 第").append(pageNumber).append("页");
            }
            sb.append(")\n");
            sb.append(excerpt).append("\n");
        }

        log.info("[Citation] assembled {} references from {} source docs", citationMap.size(), documents.size());
        citationMap.forEach((idx, c) -> log.debug("[Citation] [{}] file='{}' page={}", idx, c.filename(), c.pageNumber()));
        return new PromptWithCitations(sb.toString(), citationMap);
    }

    public CitedAnswer parseResponse(String rawAnswer, Map<Integer, Citation> citationMap) {
        List<Citation> usedCitations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(rawAnswer);
        List<Integer> seen = new ArrayList<>();

        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1));
            if (!seen.contains(idx) && citationMap.containsKey(idx)) {
                seen.add(idx);
                usedCitations.add(citationMap.get(idx));
            }
        }

        log.info("[Citation] parsed answer: found {} citation references {}", usedCitations.size(), seen);
        return new CitedAnswer(rawAnswer, usedCitations, List.of());
    }

    private static String extractString(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    private static Integer extractInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractSectionPath(Map<String, Object> metadata) {
        Object value = metadata.get("section_path");
        if (value == null) return null;
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("/"));
        }
        return value.toString();
    }
}
