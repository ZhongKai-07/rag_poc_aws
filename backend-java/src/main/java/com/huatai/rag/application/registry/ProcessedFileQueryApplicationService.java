package com.huatai.rag.application.registry;

import com.huatai.rag.api.upload.dto.ProcessedFilesResponse;
import com.huatai.rag.domain.document.DocumentFileRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.document.IngestionStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface ProcessedFileQueryApplicationService {

    default ProcessedFilesResponse getProcessedFiles() {
        return ProcessedFilesResult.toApiResponse(listProcessedFilesView());
    }

    default Map<String, String> getIndexByFilename(String filename) {
        return findIndexByFilename(filename).toApiResponse();
    }

    ProcessedFilesResult listProcessedFilesView();

    IndexLookupResult findIndexByFilename(String filename);

    record ProcessedFileResult(String filename, String indexName) {
    }

    record ProcessedFilesResult(String status, List<ProcessedFileResult> files) {

        public ProcessedFilesResult {
            files = List.copyOf(files);
        }

        private static ProcessedFilesResponse toApiResponse(ProcessedFilesResult result) {
            ProcessedFilesResponse response = new ProcessedFilesResponse();
            response.setStatus(result.status());
            response.setFiles(result.files().stream().map(file -> {
                ProcessedFilesResponse.FileRecord fileRecord = new ProcessedFilesResponse.FileRecord();
                fileRecord.setFilename(file.filename());
                fileRecord.setIndexName(file.indexName());
                return fileRecord;
            }).toList());
            return response;
        }
    }

    record IndexLookupResult(String status, String indexName, String message) {

        private Map<String, String> toApiResponse() {
            Map<String, String> response = new LinkedHashMap<>();
            response.put("status", status);
            if (indexName != null) {
                response.put("index_name", indexName);
            }
            if (message != null) {
                response.put("message", message);
            }
            return response;
        }
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
