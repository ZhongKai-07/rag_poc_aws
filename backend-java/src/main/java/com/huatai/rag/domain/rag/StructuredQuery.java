package com.huatai.rag.domain.rag;

public record StructuredQuery(
        String counterparty,
        String agreementType,
        String businessField,
        String fallbackQuery
) {}
