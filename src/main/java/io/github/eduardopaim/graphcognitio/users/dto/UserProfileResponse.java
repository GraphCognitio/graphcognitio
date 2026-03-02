package io.github.eduardopaim.graphcognitio.users.dto;

import java.time.Instant;

public record UserProfileResponse(
        String username,
        String displayName,
        String bio,
        Instant createdAt,
        String avatarUrl,
        int postsCount) {
}
