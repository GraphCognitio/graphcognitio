package io.github.eduardopaim.graphcognitio.auth.dto;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record AuthUserResponse(
                UUID id,
                String name,
                String username,
                String email,
                UserRole role,
                String bio,
                String avatarUrl,
                Instant createdAt) {
}
