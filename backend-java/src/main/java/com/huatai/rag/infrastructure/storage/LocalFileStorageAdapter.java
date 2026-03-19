package com.huatai.rag.infrastructure.storage;

import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.infrastructure.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class LocalFileStorageAdapter implements DocumentIngestionApplicationService.DocumentStorage {

    private final StorageProperties storageProperties;

    public LocalFileStorageAdapter(StorageProperties storageProperties) {
        this.storageProperties = Objects.requireNonNull(storageProperties, "storageProperties");
    }

    @Override
    public DocumentIngestionApplicationService.StoredDocument store(String filename, byte[] content, String directoryPath) {
        Path targetDirectory = resolveDirectory(directoryPath);
        try {
            Files.createDirectories(targetDirectory);
            Path targetFile = targetDirectory.resolve(filename).normalize();
            Files.write(targetFile, content);
            return new DocumentIngestionApplicationService.StoredDocument(filename, targetFile.toAbsolutePath().toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store uploaded file", exception);
        }
    }

    private Path resolveDirectory(String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return Paths.get(storageProperties.getDocumentRoot()).toAbsolutePath().normalize();
        }
        Path inputPath = Paths.get(directoryPath);
        if (inputPath.isAbsolute()) {
            return inputPath.normalize();
        }
        return Paths.get(storageProperties.getDocumentRoot())
                .toAbsolutePath()
                .resolve(directoryPath)
                .normalize();
    }
}
