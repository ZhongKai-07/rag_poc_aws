package com.huatai.rag.application.ingestion;

import java.util.Map;

public interface DocumentIngestionApplicationService {

    Map<String, Object> ingest(String filename, byte[] content, String directoryPath);
}
