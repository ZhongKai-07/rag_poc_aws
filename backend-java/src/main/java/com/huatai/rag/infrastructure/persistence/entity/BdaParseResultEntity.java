package com.huatai.rag.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bda_parse_result")
public class BdaParseResultEntity {

    @Id
    private UUID id;

    @Column(name = "document_file_id", nullable = false)
    private UUID documentFileId;

    @Column(name = "index_name", nullable = false, length = 128)
    private String indexName;

    @Column(name = "s3_output_path", nullable = false, length = 1024)
    private String s3OutputPath;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "parser_type", length = 64)
    private String parserType;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDocumentFileId() { return documentFileId; }
    public void setDocumentFileId(UUID documentFileId) { this.documentFileId = documentFileId; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getS3OutputPath() { return s3OutputPath; }
    public void setS3OutputPath(String s3OutputPath) { this.s3OutputPath = s3OutputPath; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public String getParserType() { return parserType; }
    public void setParserType(String parserType) { this.parserType = parserType; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
