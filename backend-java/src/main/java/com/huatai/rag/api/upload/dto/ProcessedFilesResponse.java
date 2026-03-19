package com.huatai.rag.api.upload.dto;

import java.util.ArrayList;
import java.util.List;

public class ProcessedFilesResponse {

    private String status;
    private List<FileRecord> files = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<FileRecord> getFiles() {
        return files;
    }

    public void setFiles(List<FileRecord> files) {
        this.files = files;
    }

    public static class FileRecord {
        private String filename;
        private String indexName;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("index_name")
        public String getIndexName() {
            return indexName;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("index_name")
        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }
    }
}
