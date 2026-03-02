package io.github.eduardopaim.graphcognitio.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexHistoryEntry;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingsStatusResponse;
import io.github.eduardopaim.graphcognitio.admin.mapper.AdminUserMapper;
import io.github.eduardopaim.graphcognitio.audit.service.AuditLogService;
import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingsHealthIndicator;
import io.github.eduardopaim.graphcognitio.entity.AuditLogEntity;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.ForbiddenException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.posts.service.PostEmbeddingService;
import io.github.eduardopaim.graphcognitio.repository.AuditLogRepository;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PostEmbeddingService postEmbeddingService;

    @Mock
    private EmbeddingsHealthIndicator embeddingsHealthIndicator;

    @Mock
    private AuditLogRepository auditLogRepository;

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

    @Test
    void searchUsersReturnsRecentUsersWhenQueryIsBlank() {
        UUID adminId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID firstId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");
        UUID secondId = UUID.fromString("2fbf8f22-5485-4f95-a9c0-1723a49bfd7c");

        UserEntity admin = user(adminId, "Admin", UserRole.ADMIN);
        UserEntity first = user(firstId, "Alice", UserRole.USER);
        UserEntity second = user(secondId, "Bob", UserRole.MOD);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findRecentUsers(any())).thenReturn(List.of(first, second));
        when(adminUserMapper.toResponse(first)).thenReturn(new AdminUserResponse(firstId, "Alice", "alice@example.com", UserRole.USER, first.getCreatedAt()));
        when(adminUserMapper.toResponse(second)).thenReturn(new AdminUserResponse(secondId, "Bob", "bob@example.com", UserRole.MOD, second.getCreatedAt()));

        List<AdminUserResponse> result = adminService.searchUsers(adminId, "   ", 8);

        assertEquals(2, result.size());
        assertEquals(firstId, result.get(0).id());
        assertEquals(secondId, result.get(1).id());
    }

    @Test
    void searchUsersUsesTextQueryWhenPresent() {
        UUID adminId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UUID targetId = UUID.fromString("338ed4fe-f51d-423d-8fd3-c35c4f740fc7");

        UserEntity admin = user(adminId, "Admin", UserRole.ADMIN);
        UserEntity target = user(targetId, "Target", UserRole.USER);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.searchAdminUsers(eq("target"), any())).thenReturn(List.of(target));
        when(adminUserMapper.toResponse(target)).thenReturn(new AdminUserResponse(targetId, "Target", "target@example.com", UserRole.USER, target.getCreatedAt()));

        List<AdminUserResponse> result = adminService.searchUsers(adminId, "target", 6);

        assertEquals(1, result.size());
        assertEquals(targetId, result.get(0).id());
    }

    @Test
    void reindexEmbeddingsRunsMissingScopeForAdmin() {
        UUID adminId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UserEntity admin = user(adminId, "Admin", UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(postEmbeddingService.reindexMissing(120)).thenReturn(42);
        when(postEmbeddingService.currentModel()).thenReturn("semantic-hash-v3");
        when(postEmbeddingService.currentDimensions()).thenReturn(192);

        EmbeddingReindexResponse result = adminService.reindexEmbeddings(adminId, "missing", 120);

        assertEquals("missing", result.scope());
        assertEquals(42, result.reindexedCount());
        verify(auditLogService).log(eq(adminId), eq("POST_EMBEDDINGS_REINDEXED"), eq("POST"), eq(null), any());
    }

    @Test
    void embeddingsStatusReturnsHealthSnapshotForAdmin() {
        UUID adminId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UserEntity admin = user(adminId, "Admin", UserRole.ADMIN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(postEmbeddingService.reindexBatchSize()).thenReturn(200);
        when(embeddingsHealthIndicator.health()).thenReturn(Health.up()
                .withDetail("enabled", true)
                .withDetail("provider", "openai")
                .withDetail("model", "text-embedding-3-small")
                .withDetail("dimensions", 1536)
                .withDetail("apiKeyConfigured", true)
                .withDetail("remoteBaseUrl", "https://api.openai.com")
                .withDetail("remotePath", "/v1/embeddings")
                .build());

        EmbeddingsStatusResponse result = adminService.embeddingsStatus(adminId);

        assertEquals("UP", result.status());
        assertEquals("openai", result.provider());
        assertEquals(1536, result.dimensions());
        assertEquals(true, result.apiKeyConfigured());
        assertEquals(200, result.reindexBatchSize());
    }

    @Test
    void embeddingsHistoryReturnsAuditEntriesForAdmin() {
        UUID adminId = UUID.fromString("d6a63297-3f0f-4da4-9014-097bb6ebcb55");
        UserEntity admin = user(adminId, "Admin", UserRole.ADMIN);
        UUID eventId = UUID.fromString("0c2b75c4-aec8-4f62-9ab4-e3a8c5b8a92d");

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(auditLogRepository.findByActionOrderByCreatedAtDesc(eq("POST_EMBEDDINGS_REINDEXED"), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(auditLog(eventId, adminId)));
        when(userRepository.findAllById(Set.of(adminId))).thenReturn(List.of(admin));

        List<EmbeddingReindexHistoryEntry> history = adminService.embeddingsHistory(adminId, 3);

        assertEquals(1, history.size());
        EmbeddingReindexHistoryEntry entry = history.get(0);
        assertEquals("missing", entry.scope());
        assertEquals("Admin", entry.actorName());
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

    private AuditLogEntity auditLog(UUID id, UUID actorUserId) {
        AuditLogEntity log = new AuditLogEntity();
        log.setId(id);
        log.setActorUserId(actorUserId);
        log.setAction("POST_EMBEDDINGS_REINDEXED");
        log.setEntityType("POST");
        log.setEntityId(UUID.randomUUID());
        log.setMetadata(Map.of(
                "scope", "missing",
                "reindexedCount", 42,
                "model", "semantic-hash-v3",
                "dimensions", 192
        ));
        log.setCreatedAt(Instant.parse("2026-03-01T18:00:00Z"));
        return log;
    }
}
