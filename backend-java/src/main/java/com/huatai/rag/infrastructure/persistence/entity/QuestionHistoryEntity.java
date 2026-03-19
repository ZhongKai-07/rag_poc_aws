package com.huatai.rag.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_history")
public class QuestionHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "index_name", nullable = false, length = 128)
    private String indexName;

    @Column(name = "question", nullable = false, length = 2000)
    private String question;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Instant getAskedAt() {
        return askedAt;
    }

    public void setAskedAt(Instant askedAt) {
        this.askedAt = askedAt;
    }
}
