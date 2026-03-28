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

    @JsonProperty("citations")
    private List<CitationDto> citations = new ArrayList<>();

    @JsonProperty("suggested_questions")
    private List<String> suggestedQuestions = new ArrayList<>();

    private String confidence;

    @JsonProperty("history_compressed")
    private boolean historyCompressed;

    @JsonProperty("session_id")
    private String sessionId;

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

    public List<CitationDto> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationDto> citations) {
        this.citations = citations;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    public void setSuggestedQuestions(List<String> suggestedQuestions) {
        this.suggestedQuestions = suggestedQuestions;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public boolean isHistoryCompressed() {
        return historyCompressed;
    }

    public void setHistoryCompressed(boolean historyCompressed) {
        this.historyCompressed = historyCompressed;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
