package com.huatai.rag.evaluation.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.evaluation.model.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagasClient {

    private static final Logger log = LoggerFactory.getLogger(RagasClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    public RagasClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Double> evaluate(List<TraceRecord> traces) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("traces", traces));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/evaluate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(120))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("RAGAS evaluation returned status {}: {}", response.statusCode(), response.body());
                return Map.of();
            }
            return parseMetrics(response.body());
        } catch (Exception e) {
            log.error("Failed to call RAGAS evaluation service: {}", e.getMessage());
            return Map.of();
        }
    }

    public Map<String, Double> parseMetrics(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            Map<String, Double> metrics = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isArray() && !value.isEmpty()) {
                    metrics.put(entry.getKey(), value.get(0).asDouble());
                }
            });
            return metrics;
        } catch (Exception e) {
            log.warn("Failed to parse RAGAS metrics: {}", e.getMessage());
            return Map.of();
        }
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
