package com.huatai.rag.domain.rag;

public record Citation(
        int index,
        String filename,
        Integer pageNumber,
        String sectionPath,
        String excerpt
) {}
