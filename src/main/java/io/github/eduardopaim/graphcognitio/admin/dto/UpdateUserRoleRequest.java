package io.github.eduardopaim.graphcognitio.admin.dto;

import io.github.eduardopaim.graphcognitio.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull UserRole role
) {
}
