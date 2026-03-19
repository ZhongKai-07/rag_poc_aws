package com.huatai.rag.domain.retrieval;

import java.util.Locale;

public enum SearchMethod {
    VECTOR,
    TEXT,
    MIX;

    public static SearchMethod fromValue(String value) {
        try {
            return SearchMethod.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported search method: " + value, exception);
        }
    }
}
