package com.huatai.rag.domain.parser;

public record ParsedAsset(
        String assetId,
        String assetType,
        String reference,
        int pageNumber) {
}
