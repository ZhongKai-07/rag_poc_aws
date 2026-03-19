package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.domain.history.QuestionHistoryPort;
import com.huatai.rag.infrastructure.persistence.entity.QuestionHistoryEntity;
import com.huatai.rag.infrastructure.persistence.repository.QuestionHistoryJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

public class QuestionHistoryPersistenceAdapter implements QuestionHistoryPort {

    private final QuestionHistoryJpaRepository questionHistoryJpaRepository;

    public QuestionHistoryPersistenceAdapter(QuestionHistoryJpaRepository questionHistoryJpaRepository) {
        this.questionHistoryJpaRepository = Objects.requireNonNull(questionHistoryJpaRepository, "questionHistoryJpaRepository");
    }

    @Override
    public void recordQuestion(String indexName, String question) {
        QuestionHistoryEntity entity = new QuestionHistoryEntity();
        entity.setId(UUID.randomUUID());
        entity.setIndexName(indexName);
        entity.setQuestion(question);
        entity.setAskedAt(Instant.now());
        questionHistoryJpaRepository.save(entity);
    }

    @Override
    public List<QuestionCount> topQuestions(String indexName, int limit) {
        return questionHistoryJpaRepository.findTopQuestionsByIndexName(indexName, PageRequest.of(0, limit)).stream()
                .map(view -> new QuestionCount(view.getQuestion(), view.getTotal()))
                .toList();
    }

    @Override
    public List<QuestionCount> topQuestionsMulti(List<String> indexNames, int limit) {
        return questionHistoryJpaRepository.findTopQuestionsByIndexNames(indexNames, PageRequest.of(0, limit)).stream()
                .map(view -> new QuestionCount(view.getQuestion(), view.getTotal()))
                .toList();
    }
}
