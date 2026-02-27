package io.github.eduardopaim.graphcognitio.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.entity.IdempotencyKeyEntity;
import io.github.eduardopaim.graphcognitio.exception.ConflictException;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyServiceTest {

    @Test
    void reusesStoredResponseForSameKeyAndPayload() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setTtlMinutes(120);

        Clock clock = Clock.fixed(Instant.parse("2026-02-27T12:00:00Z"), ZoneOffset.UTC);
        IdempotencyService service = new IdempotencyService(
                store,
                properties,
                new ObjectMapper().findAndRegisterModules(),
                clock
        );

        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        AtomicInteger supplierCalls = new AtomicInteger(0);

        PostResponse first = service.execute(
                userId,
                "idem-1",
                "/posts",
                Map.of("content", "hello"),
                201,
                PostResponse.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return postResponse("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");
                }
        );
        PostResponse second = service.execute(
                userId,
                "idem-1",
                "/posts",
                Map.of("content", "hello"),
                201,
                PostResponse.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return postResponse("aaaaaaaa-5485-4f95-a9c0-1723a49bfd7c");
                }
        );

        assertEquals(1, supplierCalls.get());
        assertEquals(first.id(), second.id());
    }

    @Test
    void throwsConflictWhenKeyReusedWithDifferentPayload() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setTtlMinutes(120);

        Clock clock = Clock.fixed(Instant.parse("2026-02-27T12:00:00Z"), ZoneOffset.UTC);
        IdempotencyService service = new IdempotencyService(
                store,
                properties,
                new ObjectMapper().findAndRegisterModules(),
                clock
        );

        UUID userId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        service.execute(
                userId,
                "idem-2",
                "/posts",
                Map.of("content", "hello"),
                201,
                PostResponse.class,
                () -> postResponse("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c")
        );

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.execute(
                        userId,
                        "idem-2",
                        "/posts",
                        Map.of("content", "different"),
                        201,
                        PostResponse.class,
                        () -> postResponse("aaaaaaaa-5485-4f95-a9c0-1723a49bfd7c")
                )
        );

        assertEquals("Idempotency-Key already used with different request", exception.getMessage());
    }

    private PostResponse postResponse(String postId) {
        UUID id = UUID.fromString(postId);
        return new PostResponse(
                id,
                UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55"),
                "Eduardo",
                "hello",
                Instant.parse("2026-02-27T12:00:00Z"),
                null,
                id,
                0,
                0,
                false
        );
    }

    private static class InMemoryIdempotencyStore implements IdempotencyStore {
        private final Map<String, IdempotencyKeyEntity> entries = new HashMap<>();

        @Override
        public Optional<IdempotencyKeyEntity> findByUserIdAndKey(UUID userId, String key) {
            return Optional.ofNullable(entries.get(userId + ":" + key));
        }

        @Override
        public IdempotencyKeyEntity save(IdempotencyKeyEntity entity) {
            entries.put(entity.getUserId() + ":" + entity.getIdempotencyKey(), entity);
            return entity;
        }

        @Override
        public void delete(IdempotencyKeyEntity entity) {
            entries.remove(entity.getUserId() + ":" + entity.getIdempotencyKey());
        }
    }
}
