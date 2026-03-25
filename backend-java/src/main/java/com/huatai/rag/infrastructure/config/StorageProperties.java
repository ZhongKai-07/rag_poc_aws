package com.huatai.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huatai.storage")
public class StorageProperties {

    private String documentRoot = "./documents";
    private String documentBucket = "";
    private String documentPrefix = "";
    private String bdaOutputPrefix = "_bda_output";

    public String getDocumentRoot() {
        return documentRoot;
    }

    public void setDocumentRoot(String documentRoot) {
        this.documentRoot = documentRoot;
    }

    public String getDocumentBucket() {
        return documentBucket;
    }

    public void setDocumentBucket(String documentBucket) {
        this.documentBucket = documentBucket;
    }

    public String getDocumentPrefix() {
        return documentPrefix;
    }

    public void setDocumentPrefix(String documentPrefix) {
        this.documentPrefix = documentPrefix;
    }

    public String getBdaOutputPrefix() {
        return bdaOutputPrefix;
    }

    public void setBdaOutputPrefix(String bdaOutputPrefix) {
        this.bdaOutputPrefix = bdaOutputPrefix;
    }
}
