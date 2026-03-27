package com.huatai.rag.evaluation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.evaluation.model.TestDataset;

import java.io.IOException;
import java.io.InputStream;

public class TestDatasetLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static TestDataset load(String classpathResource) {
        try (InputStream is = TestDatasetLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return MAPPER.readValue(is, TestDataset.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load test dataset: " + classpathResource, e);
        }
    }
}
