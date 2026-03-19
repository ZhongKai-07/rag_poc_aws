package com.huatai.rag.domain.document;

import java.util.List;
import java.util.Optional;

public interface DocumentRegistryPort {

    DocumentFileRecord saveDocument(DocumentFileRecord documentFileRecord);

    IngestionJobRecord saveIngestionJob(IngestionJobRecord ingestionJobRecord);

    Optional<DocumentFileRecord> findByFilename(String filename);

    Optional<DocumentFileRecord> findByIndexName(String indexName);

    List<DocumentFileRecord> listProcessedFiles();
}
