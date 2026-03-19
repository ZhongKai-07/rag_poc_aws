package com.huatai.rag.api.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecallDocumentDto {

    @JsonProperty("page_content")
    private String pageContent;

    private double score;

    public String getPageContent() {
        return pageContent;
    }

    public void setPageContent(String pageContent) {
        this.pageContent = pageContent;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
