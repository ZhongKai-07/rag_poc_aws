package com.huatai.rag.api.common;

import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "detail",
                exception.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Validation failed")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("detail", safeMessage(exception, "Invalid argument")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
        log.error("Infrastructure request failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "detail",
                sanitizeInfrastructureMessage(exception.getMessage())));
    }

    @ExceptionHandler(ParseResultQueryApplicationService.S3FetchException.class)
    public ResponseEntity<Map<String, Object>> handleS3Fetch(
            ParseResultQueryApplicationService.S3FetchException e) {
        log.error("S3 fetch failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("detail", "BDA output fetch failed"));
    }

    @ExceptionHandler(ParseResultQueryApplicationService.S3ObjectNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleS3ObjectNotFound(
            ParseResultQueryApplicationService.S3ObjectNotFoundException e) {
        log.warn("BDA output not found in S3: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(ParseResultQueryApplicationService.IndexNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleIndexNotFound(
            ParseResultQueryApplicationService.IndexNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception exception) {
        log.error("Unhandled request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", safeMessage(exception, "Internal server error")));
    }

    private String safeMessage(Exception exception, String fallback) {
        return exception.getMessage() != null ? exception.getMessage() : fallback;
    }

    private String sanitizeInfrastructureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed";
        }
        String lowered = message.toLowerCase();
        if (lowered.contains("bedrock")) {
            return "Bedrock request failed";
        }
        if (lowered.contains("bda") || lowered.contains("data automation")) {
            return "BDA parsing failed";
        }
        if (lowered.contains("opensearch")) {
            return "OpenSearch request failed";
        }
        return message;
    }
}
