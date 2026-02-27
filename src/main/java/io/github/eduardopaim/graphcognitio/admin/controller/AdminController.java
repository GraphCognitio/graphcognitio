package io.github.eduardopaim.graphcognitio.admin.controller;

import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.UpdateUserRoleRequest;
import io.github.eduardopaim.graphcognitio.admin.service.AdminService;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<AdminUserResponse> updateUserRole(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                            @PathVariable UUID id,
                                                            @Valid @RequestBody UpdateUserRoleRequest request) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return ResponseEntity.ok(adminService.updateUserRole(authenticatedUser.userId(), id, request.role()));
    }
}
