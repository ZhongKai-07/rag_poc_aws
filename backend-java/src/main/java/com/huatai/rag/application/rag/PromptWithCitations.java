package com.huatai.rag.application.rag;

import com.huatai.rag.domain.rag.Citation;
import java.util.Map;

public record PromptWithCitations(
        String formattedContext,
        Map<Integer, Citation> citationMap
) {}
