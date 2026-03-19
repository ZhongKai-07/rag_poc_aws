package com.huatai.rag.api.upload;

import com.huatai.rag.api.upload.dto.ProcessedFilesResponse;
import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.application.registry.ProcessedFileQueryApplicationService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {

    private final DocumentIngestionApplicationService documentIngestionApplicationService;
    private final ProcessedFileQueryApplicationService processedFileQueryApplicationService;

    public UploadController(
            DocumentIngestionApplicationService documentIngestionApplicationService,
            ProcessedFileQueryApplicationService processedFileQueryApplicationService) {
        this.documentIngestionApplicationService = documentIngestionApplicationService;
        this.processedFileQueryApplicationService = processedFileQueryApplicationService;
    }

    @PostMapping("/upload_files")
    public Map<String, Object> uploadFiles(
            @RequestParam("file") MultipartFile file,
            @RequestParam("directory_path") String directoryPath) throws IOException {
        DocumentIngestionApplicationService.IngestionResult result = documentIngestionApplicationService.handle(
                new DocumentIngestionApplicationService.IngestionCommand(
                        file.getOriginalFilename(),
                        file.getBytes(),
                        directoryPath));
        return Map.of(
                "status", result.status(),
                "message", result.message());
    }

    @GetMapping("/processed_files")
    public ProcessedFilesResponse processedFiles() {
        ProcessedFileQueryApplicationService.ProcessedFilesResult result = processedFileQueryApplicationService.listProcessedFilesView();
        ProcessedFilesResponse response = new ProcessedFilesResponse();
        response.setStatus(result.status());
        response.setFiles(result.files().stream().map(file -> {
            ProcessedFilesResponse.FileRecord record = new ProcessedFilesResponse.FileRecord();
            record.setFilename(file.filename());
            record.setIndexName(file.indexName());
            return record;
        }).toList());
        return response;
    }

    @GetMapping("/get_index/{filename}")
    public Map<String, String> getIndex(@PathVariable String filename) {
        ProcessedFileQueryApplicationService.IndexLookupResult result =
                processedFileQueryApplicationService.findIndexByFilename(filename);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", result.status());
        if (result.indexName() != null) {
            response.put("index_name", result.indexName());
        }
        if (result.message() != null) {
            response.put("message", result.message());
        }
        return response;
    }
}
