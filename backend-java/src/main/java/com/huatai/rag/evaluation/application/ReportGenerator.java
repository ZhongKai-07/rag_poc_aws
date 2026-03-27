package com.huatai.rag.evaluation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.huatai.rag.evaluation.model.EvaluationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeTo(EvaluationReport report, String outputPath) {
        try {
            Path path = Path.of(outputPath);
            Files.createDirectories(path.getParent());
            MAPPER.writeValue(path.toFile(), report);
            log.info("Evaluation report written to {}", outputPath);
        } catch (IOException e) {
            log.error("Failed to write evaluation report to {}: {}", outputPath, e.getMessage());
        }
    }
}
