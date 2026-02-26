package io.github.eduardopaim.graphcognitio.auth.dto;

public record AuthResponse(
        String accessToken,
        AuthUserResponse user
) {
}
