package io.github.eduardopaim.graphcognitio.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String name,
        String email,
        Instant createdAt
) {
}
