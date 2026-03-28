package com.huatai.rag.api.chat.dto;

public class CreateSessionRequest {
    private String title;
    private String module = "RAG";

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
}
