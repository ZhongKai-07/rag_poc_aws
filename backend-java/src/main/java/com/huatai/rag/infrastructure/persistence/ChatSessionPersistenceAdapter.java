package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.domain.chat.ChatSession;
import com.huatai.rag.domain.chat.ChatSessionPort;
import com.huatai.rag.infrastructure.persistence.entity.ChatSessionEntity;
import com.huatai.rag.infrastructure.persistence.repository.ChatSessionJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

public class ChatSessionPersistenceAdapter implements ChatSessionPort {

    private final ChatSessionJpaRepository repository;

    public ChatSessionPersistenceAdapter(ChatSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChatSession create(String title, String module) {
        var entity = new ChatSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setTitle(title);
        entity.setModule(module);
        entity = repository.save(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<ChatSession> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ChatSession> listSessions(int page, int size) {
        return repository.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, size))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void updateTitle(UUID id, String title) {
        repository.findById(id).ifPresent(entity -> {
            entity.setTitle(title);
            repository.save(entity);
        });
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private ChatSession toDomain(ChatSessionEntity entity) {
        return new ChatSession(entity.getId(), entity.getTitle(), entity.getModule(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
