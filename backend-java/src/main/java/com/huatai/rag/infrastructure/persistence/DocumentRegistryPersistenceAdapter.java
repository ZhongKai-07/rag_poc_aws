package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.domain.document.DocumentFileRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import com.huatai.rag.domain.document.IngestionJobRecord;
import com.huatai.rag.domain.document.IngestionStatus;
import com.huatai.rag.infrastructure.persistence.entity.DocumentFileEntity;
import com.huatai.rag.infrastructure.persistence.entity.IngestionJobEntity;
import com.huatai.rag.infrastructure.persistence.repository.DocumentFileJpaRepository;
import com.huatai.rag.infrastructure.persistence.repository.IngestionJobJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DocumentRegistryPersistenceAdapter implements DocumentRegistryPort {

    private final DocumentFileJpaRepository documentFileJpaRepository;
    private final IngestionJobJpaRepository ingestionJobJpaRepository;

    public DocumentRegistryPersistenceAdapter(
            DocumentFileJpaRepository documentFileJpaRepository,
            IngestionJobJpaRepository ingestionJobJpaRepository) {
        this.documentFileJpaRepository = Objects.requireNonNull(documentFileJpaRepository, "documentFileJpaRepository");
        this.ingestionJobJpaRepository = Objects.requireNonNull(ingestionJobJpaRepository, "ingestionJobJpaRepository");
    }

    @Override
    public DocumentFileRecord saveDocument(DocumentFileRecord documentFileRecord) {
        DocumentFileEntity entity = new DocumentFileEntity();
        entity.setId(documentFileRecord.id() == null ? UUID.randomUUID() : documentFileRecord.id());
        entity.setFilename(documentFileRecord.filename());
        entity.setIndexName(documentFileRecord.indexName());
        entity.setStoragePath(documentFileRecord.storagePath());
        entity.setStatus(documentFileRecord.status().name());
        entity.setCreatedAt(documentFileRecord.createdAt());
        entity.setUpdatedAt(documentFileRecord.updatedAt());
        return toRecord(documentFileJpaRepository.save(entity));
    }

    @Override
    public IngestionJobRecord saveIngestionJob(IngestionJobRecord ingestionJobRecord) {
        IngestionJobEntity entity = new IngestionJobEntity();
        entity.setId(ingestionJobRecord.id() == null ? UUID.randomUUID() : ingestionJobRecord.id());
        entity.setDocumentFileId(ingestionJobRecord.documentFileId());
        entity.setIndexName(ingestionJobRecord.indexName());
        entity.setStatus(ingestionJobRecord.status().name());
        entity.setCreatedAt(ingestionJobRecord.createdAt());
        entity.setUpdatedAt(ingestionJobRecord.updatedAt());
        return toRecord(ingestionJobJpaRepository.save(entity));
    }

    @Override
    public Optional<DocumentFileRecord> findByFilename(String filename) {
        return documentFileJpaRepository.findByFilename(filename).map(this::toRecord);
    }

    @Override
    public Optional<DocumentFileRecord> findByIndexName(String indexName) {
        return documentFileJpaRepository.findByIndexName(indexName).map(this::toRecord);
    }

    @Override
    public List<DocumentFileRecord> listProcessedFiles() {
        return documentFileJpaRepository.findAll().stream().map(this::toRecord).toList();
    }

    private DocumentFileRecord toRecord(DocumentFileEntity entity) {
        return new DocumentFileRecord(
                entity.getId(),
                entity.getFilename(),
                entity.getIndexName(),
                entity.getStoragePath(),
                IngestionStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private IngestionJobRecord toRecord(IngestionJobEntity entity) {
        return new IngestionJobRecord(
                entity.getId(),
                entity.getDocumentFileId(),
                entity.getIndexName(),
                IngestionStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
