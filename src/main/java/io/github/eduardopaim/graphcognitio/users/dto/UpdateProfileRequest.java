package io.github.eduardopaim.graphcognitio.users.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters") String name,

        @Size(max = 160, message = "Bio must be at most 160 characters") String bio,

        String avatarUrl) {
}
