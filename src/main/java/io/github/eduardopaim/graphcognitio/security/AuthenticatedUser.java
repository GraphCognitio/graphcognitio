package io.github.eduardopaim.graphcognitio.security;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, UserRole role) {
}
