package com.huatai.rag.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.chat.ChatMessage;
import com.huatai.rag.domain.chat.ChatMessagePort;
import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.infrastructure.persistence.entity.ChatMessageEntity;
import com.huatai.rag.infrastructure.persistence.repository.ChatMessageJpaRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChatMessagePersistenceAdapter implements ChatMessagePort {

    private final ChatMessageJpaRepository repository;
    private final ObjectMapper objectMapper;

    public ChatMessagePersistenceAdapter(ChatMessageJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ChatMessage message) {
        var entity = new ChatMessageEntity();
        entity.setId(message.id());
        entity.setSessionId(message.sessionId());
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setCitations(toJson(message.citations()));
        entity.setSuggestedQuestions(toJson(message.suggestedQuestions()));
        entity.setCreatedAt(message.createdAt());
        repository.save(entity);
    }

    @Override
    public List<ChatMessage> loadRecent(UUID sessionId, int limit) {
        var entities = repository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(entities);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public int countMessages(UUID sessionId) {
        return repository.countBySessionId(sessionId);
    }

    @Override
    public List<ChatMessage> loadAll(UUID sessionId) {
        return repository.findBySessionIdOrderByCreatedAt(sessionId)
                .stream().map(this::toDomain).toList();
    }

    private ChatMessage toDomain(ChatMessageEntity entity) {
        return new ChatMessage(
                entity.getId(),
                entity.getSessionId(),
                entity.getRole(),
                entity.getContent(),
                fromJsonCitations(entity.getCitations()),
                fromJsonStrings(entity.getSuggestedQuestions()),
                entity.getCreatedAt());
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<Citation> fromJsonCitations(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> fromJsonStrings(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
