package com.huatai.rag.api.upload;

import com.huatai.rag.api.upload.dto.ProcessedFilesResponse;
import com.huatai.rag.application.ingestion.DocumentIngestionApplicationService;
import com.huatai.rag.application.registry.ProcessedFileQueryApplicationService;
import java.io.IOException;
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
        return documentIngestionApplicationService.ingest(file.getOriginalFilename(), file.getBytes(), directoryPath);
    }

    @GetMapping("/processed_files")
    public ProcessedFilesResponse processedFiles() {
        return processedFileQueryApplicationService.getProcessedFiles();
    }

    @GetMapping("/get_index/{filename}")
    public Map<String, String> getIndex(@PathVariable String filename) {
        return processedFileQueryApplicationService.getIndexByFilename(filename);
    }
}
