package io.github.eduardopaim.graphcognitio.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eduardopaim.graphcognitio.entity.IdempotencyKeyEntity;
import io.github.eduardopaim.graphcognitio.exception.BadRequestException;
import io.github.eduardopaim.graphcognitio.exception.ConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyStore idempotencyStore;
    private final IdempotencyProperties idempotencyProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public IdempotencyService(IdempotencyStore idempotencyStore,
                              IdempotencyProperties idempotencyProperties,
                              ObjectMapper objectMapper) {
        this(idempotencyStore, idempotencyProperties, objectMapper, Clock.systemUTC());
    }

    IdempotencyService(IdempotencyStore idempotencyStore,
                       IdempotencyProperties idempotencyProperties,
                       ObjectMapper objectMapper,
                       Clock clock) {
        this.idempotencyStore = idempotencyStore;
        this.idempotencyProperties = idempotencyProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public <T> T execute(UUID userId,
                         String idempotencyKey,
                         String endpoint,
                         Object requestPayload,
                         int statusCode,
                         Class<T> responseType,
                         Supplier<T> action) {
        if (!idempotencyProperties.isEnabled() || !StringUtils.hasText(idempotencyKey)) {
            return action.get();
        }

        String normalizedKey = normalizeKey(idempotencyKey);
        String requestHash = requestHash(endpoint, requestPayload);
        Instant now = Instant.now(clock);

        IdempotencyKeyEntity existing = idempotencyStore.findByUserIdAndKey(userId, normalizedKey).orElse(null);
        if (existing != null) {
            if (existing.getExpiresAt().isAfter(now)) {
                validateSameRequest(existing, endpoint, requestHash);
                return deserialize(existing.getResponseBody(), responseType);
            }
            idempotencyStore.delete(existing);
        }

        T response = action.get();
        IdempotencyKeyEntity entry = new IdempotencyKeyEntity();
        entry.setUserId(userId);
        entry.setIdempotencyKey(normalizedKey);
        entry.setEndpoint(endpoint);
        entry.setRequestHash(requestHash);
        entry.setResponseBody(serialize(response));
        entry.setStatusCode(statusCode);
        entry.setCreatedAt(now);
        entry.setExpiresAt(now.plus(idempotencyProperties.getTtlMinutes(), ChronoUnit.MINUTES));
        idempotencyStore.save(entry);
        return response;
    }

    private String normalizeKey(String key) {
        String normalized = key.trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException("Idempotency-Key cannot be blank");
        }
        if (normalized.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key is too long");
        }
        return normalized;
    }

    private void validateSameRequest(IdempotencyKeyEntity existing, String endpoint, String requestHash) {
        if (!existing.getEndpoint().equals(endpoint) || !existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency-Key already used with different request");
        }
    }

    private String requestHash(String endpoint, Object payload) {
        String requestJson = payload == null ? "" : serialize(payload);
        String raw = endpoint + "|" + requestJson;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private <T> T deserialize(String json, Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotent response", ex);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotent payload", ex);
        }
    }
}
