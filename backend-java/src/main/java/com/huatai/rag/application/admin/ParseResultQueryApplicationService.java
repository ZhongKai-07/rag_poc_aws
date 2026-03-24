package com.huatai.rag.application.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

public class ParseResultQueryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ParseResultQueryApplicationService.class);

    private final BdaParseResultPort bdaParseResultPort;
    private final DocumentRegistryPort documentRegistryPort;
    private final S3Client s3Client;
    private final RestClient openSearchRestClient;
    private final ObjectMapper objectMapper;

    public ParseResultQueryApplicationService(
            BdaParseResultPort bdaParseResultPort,
            DocumentRegistryPort documentRegistryPort,
            S3Client s3Client,
            RestClient openSearchRestClient,
            ObjectMapper objectMapper) {
        this.bdaParseResultPort = Objects.requireNonNull(bdaParseResultPort);
        this.documentRegistryPort = Objects.requireNonNull(documentRegistryPort);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.openSearchRestClient = Objects.requireNonNull(openSearchRestClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // --- domain records for responses ---

    public record ParseResultSummary(
            String indexName,
            String filename,
            int chunkCount,
            int pageCount,
            String parserType,
            String parserVersion,
            Instant createdAt) {}

    public record IndexedChunk(
            String chunkId,
            int pageNumber,
            List<String> sectionPath,
            String paragraph,
            String sentence,
            List<String> assetReferences) {}

    // --- query methods ---

    public List<ParseResultSummary> listAll() {
        return bdaParseResultPort.findAll().stream()
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .map(record -> {
                    String filename = documentRegistryPort
                            .findByIndexName(record.indexName())
                            .map(doc -> doc.filename())
                            .orElse(record.indexName());
                    return new ParseResultSummary(
                            record.indexName(),
                            filename,
                            record.chunkCount(),
                            record.pageCount(),
                            record.parserType(),
                            record.parserVersion(),
                            record.createdAt());
                })
                .toList();
    }

    public Optional<JsonNode> fetchRawBdaJson(String indexName) {
        Optional<BdaParseResultRecord> record = bdaParseResultPort.findLatestByIndexName(indexName);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        String s3Uri = record.get().s3OutputPath();
        try {
            S3Location loc = S3Location.parse(s3Uri);
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(loc.bucket()).key(loc.key()).build()
            ).asByteArray();
            return Optional.of(objectMapper.readTree(bytes));
        } catch (NoSuchKeyException e) {
            log.warn("BDA result.json not found in S3: {}", s3Uri);
            throw new S3ObjectNotFoundException("BDA output not found in S3");
        } catch (Exception e) {
            log.error("Failed to fetch BDA JSON from S3: {}", s3Uri, e);
            throw new S3FetchException("Failed to fetch BDA output from S3", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<IndexedChunk> fetchIndexedChunks(String indexName) {
        String body = """
                {"query":{"match_all":{}},"size":200,"_source":{"excludes":["sentence_vector"]}}
                """;
        Request request = new Request("POST", "/" + indexName + "/_search");
        request.setJsonEntity(body);
        try {
            var response = openSearchRestClient.performRequest(request);
            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getEntity().getContent(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Map<String, Object> hitsContainer = (Map<String, Object>) responseBody.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsContainer.getOrDefault("hits", List.of());
            return hits.stream().map(hit -> {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                Map<String, Object> metadata = (Map<String, Object>) source.getOrDefault("metadata", Map.of());
                return new IndexedChunk(
                        (String) metadata.getOrDefault("chunk_id", ""),
                        ((Number) metadata.getOrDefault("page_number", 0)).intValue(),
                        (List<String>) metadata.getOrDefault("section_path", List.of()),
                        (String) source.getOrDefault("paragraph", ""),
                        (String) source.getOrDefault("sentence", ""),
                        (List<String>) metadata.getOrDefault("asset_references", List.of()));
            }).toList();
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                throw new IndexNotFoundException("OpenSearch index not found: " + indexName);
            }
            throw new IllegalStateException("OpenSearch search failed", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("OpenSearch search I/O failure", e);
        }
    }

    // --- exceptions ---
    public static class S3ObjectNotFoundException extends RuntimeException {
        public S3ObjectNotFoundException(String msg) { super(msg); }
    }
    public static class S3FetchException extends RuntimeException {
        public S3FetchException(String msg) { super(msg); }
        public S3FetchException(String msg, Throwable cause) { super(msg, cause); }
    }
    public static class IndexNotFoundException extends RuntimeException {
        public IndexNotFoundException(String msg) { super(msg); }
    }

    // --- S3 URI parser ---
    private record S3Location(String bucket, String key) {
        static S3Location parse(String uri) {
            if (uri == null || !uri.startsWith("s3://")) {
                throw new IllegalArgumentException("Expected S3 URI: " + uri);
            }
            String withoutScheme = uri.substring(5);
            int sep = withoutScheme.indexOf('/');
            return new S3Location(withoutScheme.substring(0, sep), withoutScheme.substring(sep + 1));
        }
    }
}
