package com.huatai.rag.application.registry;

import com.huatai.rag.domain.document.DocumentFileRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.document.IngestionStatus;
import java.util.List;
import java.util.Objects;

public interface ProcessedFileQueryApplicationService {

    ProcessedFilesResult listProcessedFilesView();

    IndexLookupResult findIndexByFilename(String filename);

    record ProcessedFileResult(String filename, String indexName) {
    }

    record ProcessedFilesResult(String status, List<ProcessedFileResult> files) {

        public ProcessedFilesResult {
            files = List.copyOf(files);
        }
    }

    record IndexLookupResult(String status, String indexName, String message) {
    }

    final class Default implements ProcessedFileQueryApplicationService {
        private final DocumentRegistryPort documentRegistryPort;

        public Default(DocumentRegistryPort documentRegistryPort) {
            this.documentRegistryPort = Objects.requireNonNull(documentRegistryPort, "documentRegistryPort");
        }

        @Override
        public ProcessedFilesResult listProcessedFilesView() {
            return new ProcessedFilesResult(
                    "success",
                    documentRegistryPort.listProcessedFiles().stream()
                            .filter(record -> record.status() == IngestionStatus.COMPLETED)
                            .map(record -> new ProcessedFileResult(record.filename(), record.indexName()))
                            .toList());
        }

        @Override
        public IndexLookupResult findIndexByFilename(String filename) {
            return documentRegistryPort.findByFilename(filename)
                    .map(record -> new IndexLookupResult("success", record.indexName(), null))
                    .orElseGet(() -> new IndexLookupResult("error", null, "File not found"));
        }
    }
}
