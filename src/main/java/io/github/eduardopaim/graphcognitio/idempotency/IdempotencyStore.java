package io.github.eduardopaim.graphcognitio.idempotency;

import io.github.eduardopaim.graphcognitio.entity.IdempotencyKeyEntity;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {

    Optional<IdempotencyKeyEntity> findByUserIdAndKey(UUID userId, String key);

    IdempotencyKeyEntity save(IdempotencyKeyEntity entity);

    void delete(IdempotencyKeyEntity entity);
}
