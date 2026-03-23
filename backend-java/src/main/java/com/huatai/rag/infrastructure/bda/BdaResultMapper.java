package com.huatai.rag.infrastructure.bda;

import com.fasterxml.jackson.databind.JsonNode;
import com.huatai.rag.domain.parser.ParsedAsset;
import com.huatai.rag.domain.parser.ParsedChunk;
import com.huatai.rag.domain.parser.ParsedDocument;
import com.huatai.rag.domain.parser.ParsedPage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BdaResultMapper {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    public ParsedDocument map(JsonNode payload, String fileName, String indexName, String s3OutputPath) {
        JsonNode documentNode = payload.path("document");
        JsonNode pagesNode = selectPagesNode(payload, documentNode);
        Map<String, ParsedAsset> assetsById = mapAssets(documentNode.path("assets"));
        Map<Integer, List<String>> pageSections = mapPageSections(pagesNode);

        List<ParsedPage> pages = mapPages(pagesNode);
        String pType = parserType(payload);
        String pVersion = parserVersion(payload);
        List<ParsedChunk> chunks = mapChunks(
                documentNode.path("chunks"),
                pagesNode,
                assetsById,
                pageSections,
                fileName,
                pType,
                pVersion);

        return new ParsedDocument(
                fileName,
                indexName,
                pages,
                chunks,
                new ArrayList<>(assetsById.values()),
                s3OutputPath,
                pType,
                pVersion);
    }

    private Map<String, ParsedAsset> mapAssets(JsonNode assetsNode) {
        if (!assetsNode.isArray()) {
            return Collections.emptyMap();
        }

        Map<String, ParsedAsset> assetsById = new LinkedHashMap<>();
        for (JsonNode assetNode : assetsNode) {
            ParsedAsset asset = new ParsedAsset(
                    assetNode.path("assetId").asText(),
                    assetNode.path("assetType").asText(),
                    assetNode.path("reference").asText(),
                    assetNode.path("pageNumber").asInt());
            assetsById.put(asset.assetId(), asset);
        }
        return assetsById;
    }

    private Map<Integer, List<String>> mapPageSections(JsonNode pagesNode) {
        if (!pagesNode.isArray()) {
            return Collections.emptyMap();
        }

        Map<Integer, List<String>> pageSections = new LinkedHashMap<>();
        for (JsonNode pageNode : pagesNode) {
            pageSections.put(pageNumber(pageNode), readStringList(pageNode.path("sectionPath")));
        }
        return pageSections;
    }

    private List<ParsedPage> mapPages(JsonNode pagesNode) {
        if (!pagesNode.isArray()) {
            return List.of();
        }

        List<ParsedPage> pages = new ArrayList<>();
        for (JsonNode pageNode : pagesNode) {
            pages.add(new ParsedPage(
                    pageNumber(pageNode),
                    pageText(pageNode),
                    readStringList(pageNode.path("sectionPath"))));
        }
        return pages;
    }

    private List<ParsedChunk> mapChunks(
            JsonNode chunksNode,
            JsonNode pagesNode,
            Map<String, ParsedAsset> assetsById,
            Map<Integer, List<String>> pageSections,
            String fileName,
            String parserType,
            String parserVersion) {
        if (!chunksNode.isArray() || chunksNode.isEmpty()) {
            return deriveChunksFromPages(pagesNode, pageSections, fileName, parserType, parserVersion);
        }

        List<ParsedChunk> chunks = new ArrayList<>();
        for (JsonNode chunkNode : chunksNode) {
            int pageNumber = chunkNode.path("pageNumber").asInt();
            String chunkId = chunkNode.path("chunkId").asText();
            String paragraph = normalizeWhitespace(chunkNode.path("paragraph").asText(""));
            List<String> sectionPath = readStringList(chunkNode.path("sectionPath"));
            if (sectionPath.isEmpty()) {
                sectionPath = pageSections.getOrDefault(pageNumber, List.of());
            }

            List<ParsedAsset> chunkAssets = readStringList(chunkNode.path("assetIds")).stream()
                    .map(assetsById::get)
                    .filter(Objects::nonNull)
                    .toList();

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source", fileName);
            metadata.put("chunk_id", chunkId);
            metadata.put("parser_type", parserType);
            metadata.put("parser_version", parserVersion);

            chunks.add(new ParsedChunk(
                    chunkId,
                    pageNumber,
                    paragraph,
                    sentenceText(chunkNode, paragraph),
                    sectionPath,
                    chunkAssets,
                    metadata));
        }
        return chunks;
    }

    private List<ParsedChunk> deriveChunksFromPages(
            JsonNode pagesNode,
            Map<Integer, List<String>> pageSections,
            String fileName,
            String parserType,
            String parserVersion) {
        if (!pagesNode.isArray()) {
            return List.of();
        }

        List<ParsedChunk> chunks = new ArrayList<>();
        for (JsonNode pageNode : pagesNode) {
            int pageNumber = pageNumber(pageNode);
            String paragraph = pageText(pageNode);
            if (paragraph.isBlank()) {
                continue;
            }

            String chunkId = normalizeWhitespace(pageNode.path("id").asText(""));
            if (chunkId.isBlank()) {
                chunkId = "page-" + pageNumber;
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source", fileName);
            metadata.put("chunk_id", chunkId);
            metadata.put("parser_type", parserType);
            metadata.put("parser_version", parserVersion);

            List<String> sectionPath = readStringList(pageNode.path("sectionPath"));
            if (sectionPath.isEmpty()) {
                sectionPath = pageSections.getOrDefault(pageNumber, List.of());
            }

            chunks.add(new ParsedChunk(
                    chunkId,
                    pageNumber,
                    paragraph,
                    sentenceText(paragraph),
                    sectionPath,
                    List.of(),
                    metadata));
        }
        return chunks;
    }

    private String sentenceText(JsonNode chunkNode, String paragraph) {
        String explicitSentence = normalizeWhitespace(chunkNode.path("summarySentence").asText(""));
        if (!explicitSentence.isBlank()) {
            return explicitSentence;
        }

        String normalizedParagraph = normalizeWhitespace(paragraph);
        if (normalizedParagraph.isBlank()) {
            return "";
        }

        String[] sentences = SENTENCE_BOUNDARY.split(normalizedParagraph, 2);
        return sentences.length == 0 ? normalizedParagraph : sentences[0];
    }

    private String sentenceText(String paragraph) {
        String normalizedParagraph = normalizeWhitespace(paragraph);
        if (normalizedParagraph.isBlank()) {
            return "";
        }

        String[] sentences = SENTENCE_BOUNDARY.split(normalizedParagraph, 2);
        return sentences.length == 0 ? normalizedParagraph : sentences[0];
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return stream(node).stream()
                .map(JsonNode::asText)
                .map(this::normalizeWhitespace)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String parserType(JsonNode payload) {
        String value = normalizeWhitespace(payload.path("job").path("parser").path("type").asText(""));
        if (!value.isBlank()) {
            return value;
        }
        value = normalizeWhitespace(payload.path("metadata").path("parser").path("type").asText(""));
        return value.isBlank() ? "aws-bda" : value;
    }

    private String parserVersion(JsonNode payload) {
        String value = normalizeWhitespace(payload.path("job").path("parser").path("version").asText(""));
        if (!value.isBlank()) {
            return value;
        }
        value = normalizeWhitespace(payload.path("metadata").path("parser").path("version").asText(""));
        return value.isBlank() ? "unknown" : value;
    }

    private String normalizeWhitespace(String value) {
        return Optional.ofNullable(value)
                .orElse("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private JsonNode selectPagesNode(JsonNode payload, JsonNode documentNode) {
        JsonNode legacyPages = documentNode.path("pages");
        if (legacyPages.isArray() && !legacyPages.isEmpty()) {
            return legacyPages;
        }
        return payload.path("pages");
    }

    private int pageNumber(JsonNode pageNode) {
        if (pageNode.hasNonNull("pageNumber")) {
            return pageNode.path("pageNumber").asInt();
        }
        if (pageNode.hasNonNull("page_index")) {
            return pageNode.path("page_index").asInt() + 1;
        }
        return 0;
    }

    private String pageText(JsonNode pageNode) {
        String text = normalizeWhitespace(pageNode.path("text").asText(""));
        if (!text.isBlank()) {
            return text;
        }
        return normalizeWhitespace(pageNode.path("representation").path("text").asText(""));
    }

    private List<JsonNode> stream(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        node.elements().forEachRemaining(values::add);
        return values;
    }
}
