package com.huatai.rag.application.registry;

import com.huatai.rag.api.upload.dto.ProcessedFilesResponse;
import java.util.Map;

public interface ProcessedFileQueryApplicationService {

    ProcessedFilesResponse getProcessedFiles();

    Map<String, String> getIndexByFilename(String filename);
}
