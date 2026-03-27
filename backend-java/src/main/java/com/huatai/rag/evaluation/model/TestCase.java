package com.huatai.rag.evaluation.model;

import com.huatai.rag.domain.rag.StructuredQuery;
import java.util.List;

public record TestCase(
        String id,
        String module,
        String query,
        List<String> expectedKeywords,
        StructuredQuery expectedStructured,
        List<ExpectedSource> expectedSources,
        String referenceAnswer,
        String difficulty
) {}
