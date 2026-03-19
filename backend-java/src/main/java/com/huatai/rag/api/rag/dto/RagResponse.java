package com.huatai.rag.api.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class RagResponse {

    private String answer;

    @JsonProperty("source_documents")
    private List<SourceDocumentDto> sourceDocuments = new ArrayList<>();

    @JsonProperty("recall_documents")
    private List<RecallDocumentDto> recallDocuments = new ArrayList<>();

    @JsonProperty("rerank_documents")
    private List<SourceDocumentDto> rerankDocuments = new ArrayList<>();

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceDocumentDto> getSourceDocuments() {
        return sourceDocuments;
    }

    public void setSourceDocuments(List<SourceDocumentDto> sourceDocuments) {
        this.sourceDocuments = sourceDocuments;
    }

    public List<RecallDocumentDto> getRecallDocuments() {
        return recallDocuments;
    }

    public void setRecallDocuments(List<RecallDocumentDto> recallDocuments) {
        this.recallDocuments = recallDocuments;
    }

    public List<SourceDocumentDto> getRerankDocuments() {
        return rerankDocuments;
    }

    public void setRerankDocuments(List<SourceDocumentDto> rerankDocuments) {
        this.rerankDocuments = rerankDocuments;
    }
}
