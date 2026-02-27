package io.github.eduardopaim.graphcognitio.idempotency;

import io.github.eduardopaim.graphcognitio.entity.IdempotencyKeyEntity;
import io.github.eduardopaim.graphcognitio.repository.IdempotencyKeyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyKeyRepository repository;

    public JpaIdempotencyStore(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdempotencyKeyEntity> findByUserIdAndKey(UUID userId, String key) {
        return repository.findByUserIdAndIdempotencyKey(userId, key);
    }

    @Override
    public IdempotencyKeyEntity save(IdempotencyKeyEntity entity) {
        return repository.save(entity);
    }

    @Override
    public void delete(IdempotencyKeyEntity entity) {
        repository.delete(entity);
    }
}
