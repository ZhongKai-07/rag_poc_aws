package com.huatai.rag.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ParseResultQueryApplicationService parseResultQueryService;

    public AdminController(ParseResultQueryApplicationService parseResultQueryService) {
        this.parseResultQueryService = parseResultQueryService;
    }

    @GetMapping("/parse_results")
    public List<Map<String, Object>> listParseResults() {
        return parseResultQueryService.listAll().stream()
                .map(s -> Map.<String, Object>of(
                        "index_name", s.indexName(),
                        "filename", s.filename(),
                        "chunk_count", s.chunkCount(),
                        "page_count", s.pageCount(),
                        "parser_type", s.parserType() != null ? s.parserType() : "",
                        "parser_version", s.parserVersion() != null ? s.parserVersion() : "",
                        "created_at", s.createdAt().toString()))
                .toList();
    }

    @GetMapping("/parse_results/{indexName}/raw")
    public ResponseEntity<JsonNode> getRawBdaJson(@PathVariable String indexName) {
        Optional<JsonNode> result = parseResultQueryService.fetchRawBdaJson(indexName);
        return result.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/parse_results/{indexName}/chunks")
    public List<Map<String, Object>> getIndexedChunks(@PathVariable String indexName) {
        return parseResultQueryService.fetchIndexedChunks(indexName).stream()
                .map(c -> Map.<String, Object>of(
                        "chunk_id", c.chunkId(),
                        "page_number", c.pageNumber(),
                        "section_path", c.sectionPath(),
                        "paragraph", c.paragraph(),
                        "sentence", c.sentence(),
                        "asset_references", c.assetReferences()))
                .toList();
    }
}
