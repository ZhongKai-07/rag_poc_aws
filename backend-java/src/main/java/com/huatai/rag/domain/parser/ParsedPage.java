package com.huatai.rag.domain.parser;

import java.util.List;

public record ParsedPage(
        int pageNumber,
        String text,
        List<String> sectionPath) {

    public ParsedPage {
        sectionPath = List.copyOf(sectionPath);
    }
}
