package com.huatai.rag.api.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class RagRequest {

    @JsonProperty("session_id")
    @NotBlank
    private String sessionId;

    @JsonProperty("index_names")
    @NotEmpty
    private List<String> indexNames;

    @NotBlank
    private String query;

    private String module = "RAG";

    @JsonProperty("vec_docs_num")
    private int vecDocsNum = 3;

    @JsonProperty("txt_docs_num")
    private int txtDocsNum = 3;

    @JsonProperty("vec_score_threshold")
    private double vecScoreThreshold = 0.0;

    @JsonProperty("text_score_threshold")
    private double textScoreThreshold = 0.0;

    @JsonProperty("rerank_score_threshold")
    private double rerankScoreThreshold = 0.5;

    @JsonProperty("search_method")
    private String searchMethod = "vector";

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getIndexNames() {
        return indexNames;
    }

    public void setIndexNames(List<String> indexNames) {
        this.indexNames = indexNames;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public int getVecDocsNum() {
        return vecDocsNum;
    }

    public void setVecDocsNum(int vecDocsNum) {
        this.vecDocsNum = vecDocsNum;
    }

    public int getTxtDocsNum() {
        return txtDocsNum;
    }

    public void setTxtDocsNum(int txtDocsNum) {
        this.txtDocsNum = txtDocsNum;
    }

    public double getVecScoreThreshold() {
        return vecScoreThreshold;
    }

    public void setVecScoreThreshold(double vecScoreThreshold) {
        this.vecScoreThreshold = vecScoreThreshold;
    }

    public double getTextScoreThreshold() {
        return textScoreThreshold;
    }

    public void setTextScoreThreshold(double textScoreThreshold) {
        this.textScoreThreshold = textScoreThreshold;
    }

    public double getRerankScoreThreshold() {
        return rerankScoreThreshold;
    }

    public void setRerankScoreThreshold(double rerankScoreThreshold) {
        this.rerankScoreThreshold = rerankScoreThreshold;
    }

    public String getSearchMethod() {
        return searchMethod;
    }

    public void setSearchMethod(String searchMethod) {
        this.searchMethod = searchMethod;
    }
}
