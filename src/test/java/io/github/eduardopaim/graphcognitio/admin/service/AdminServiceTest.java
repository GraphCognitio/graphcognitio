package io.github.eduardopaim.graphcognitio.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
import io.github.eduardopaim.graphcognitio.admin.mapper.AdminUserMapper;
import io.github.eduardopaim.graphcognitio.audit.service.AuditLogService;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.ForbiddenException;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void updateUserRoleThrowsForbiddenWhenActorIsNotAdmin() {
        UUID actorId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID targetId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        UserEntity actor = user(actorId, "Actor", UserRole.USER);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> adminService.updateUserRole(actorId, targetId, UserRole.MOD)
        );

        assertEquals("Admin role required", exception.getMessage());
    }

    @Test
    void updateUserRoleUpdatesTargetAndWritesAudit() {
        UUID adminId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID targetId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        UserEntity admin = user(adminId, "Admin", UserRole.ADMIN);
        UserEntity target = user(targetId, "Target", UserRole.USER);

        AdminUserResponse response = new AdminUserResponse(
                targetId,
                "Target",
                "target@example.com",
                UserRole.MOD,
                Instant.parse("2026-02-26T21:10:00Z")
        );

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminUserMapper.toResponse(target)).thenReturn(response);

        AdminUserResponse result = adminService.updateUserRole(adminId, targetId, UserRole.MOD);

        assertEquals(UserRole.MOD, target.getRole());
        assertEquals(UserRole.MOD, result.role());
        verify(auditLogService).log(eq(adminId), eq("USER_ROLE_UPDATED"), eq("USER"), eq(targetId), any());
    }

    private UserEntity user(UUID id, String name, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setCreatedAt(Instant.parse("2026-02-26T21:10:00Z"));
        return user;
    }
}
