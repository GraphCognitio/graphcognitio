package io.github.eduardopaim.graphcognitio.admin.service;

import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingsStatusResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexHistoryEntry;
import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AdminUserMapper adminUserMapper;
    private final AuditLogService auditLogService;
    private final PostEmbeddingService postEmbeddingService;
    private final EmbeddingsHealthIndicator embeddingsHealthIndicator;
    private final AuditLogRepository auditLogRepository;

    public AdminService(UserRepository userRepository,
                        AdminUserMapper adminUserMapper,
                        AuditLogService auditLogService,
                        PostEmbeddingService postEmbeddingService,
                        EmbeddingsHealthIndicator embeddingsHealthIndicator,
                        AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.adminUserMapper = adminUserMapper;
        this.auditLogService = auditLogService;
        this.postEmbeddingService = postEmbeddingService;
        this.embeddingsHealthIndicator = embeddingsHealthIndicator;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AdminUserResponse updateUserRole(UUID actorUserId, UUID targetUserId, UserRole newRole) {
        UserEntity actor = requireAdmin(actorUserId);

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserRole previousRole = target.getRole();
        target.setRole(newRole);
        UserEntity savedUser = userRepository.save(target);

        auditLogService.log(
                actor.getId(),
                "USER_ROLE_UPDATED",
                "USER",
                savedUser.getId(),
                Map.of(
                        "previousRole", previousRole.name(),
                        "newRole", newRole.name()
                )
        );

        return adminUserMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> searchUsers(UUID actorUserId, String query, int limit) {
        requireAdmin(actorUserId);

        int safeLimit = Math.min(Math.max(limit, 1), 20);
        Pageable pageable = PageRequest.of(0, safeLimit);
        String normalizedQuery = query == null ? "" : query.trim();

        List<UserEntity> users = normalizedQuery.isBlank()
                ? userRepository.findRecentUsers(pageable)
                : userRepository.searchAdminUsers(normalizedQuery, pageable);

        return users.stream()
                .map(adminUserMapper::toResponse)
                .toList();
    }

    @Transactional
    public EmbeddingReindexResponse reindexEmbeddings(UUID actorUserId, String scope, int limit) {
        UserEntity actor = requireAdmin(actorUserId);

        String normalizedScope = scope == null ? "missing" : scope.trim().toLowerCase();
        boolean missingOnly = !"all".equals(normalizedScope);
        int reindexedCount = missingOnly
                ? postEmbeddingService.reindexMissing(limit)
                : postEmbeddingService.reindexAll(limit);

        auditLogService.log(
                actor.getId(),
                "POST_EMBEDDINGS_REINDEXED",
                "POST",
                null,
                Map.of(
                        "scope", missingOnly ? "missing" : "all",
                        "reindexedCount", reindexedCount,
                        "model", postEmbeddingService.currentModel(),
                        "dimensions", postEmbeddingService.currentDimensions()
                )
        );

        return new EmbeddingReindexResponse(
                missingOnly ? "missing" : "all",
                reindexedCount,
                postEmbeddingService.currentModel(),
                postEmbeddingService.currentDimensions()
        );
    }

    @Transactional(readOnly = true)
    public EmbeddingsStatusResponse embeddingsStatus(UUID actorUserId) {
        requireAdmin(actorUserId);

        Health health = embeddingsHealthIndicator.health();
        return new EmbeddingsStatusResponse(
                health.getStatus().getCode(),
                booleanDetail(health, "enabled"),
                stringDetail(health, "provider"),
                stringDetail(health, "model"),
                integerDetail(health, "dimensions"),
                postEmbeddingService.reindexBatchSize(),
                "openai".equalsIgnoreCase(stringDetail(health, "provider")),
                booleanDetailNullable(health, "apiKeyConfigured"),
                stringDetail(health, "remoteBaseUrl"),
                stringDetail(health, "remotePath"),
                stringDetail(health, "reason")
        );
    }

    @Transactional(readOnly = true)
    public List<EmbeddingReindexHistoryEntry> embeddingsHistory(UUID actorUserId, int requestedLimit) {
        requireAdmin(actorUserId);

        int safeLimit = Math.max(1, Math.min(requestedLimit, 10));
        Pageable pageable = PageRequest.of(0, safeLimit);
        List<AuditLogEntity> auditLogs = auditLogRepository.findByActionOrderByCreatedAtDesc(EMBEDDINGS_REINDEXED_ACTION, pageable);

        Set<UUID> actorIds = auditLogs.stream()
                .map(AuditLogEntity::getActorUserId)
                .collect(Collectors.toSet());
        Map<UUID, String> actorNames = userRepository.findAllById(actorIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName, (first, second) -> first));

        return auditLogs.stream()
                .map(log -> {
                    Map<String, Object> metadata = log.getMetadata();
                    return new EmbeddingReindexHistoryEntry(
                            log.getId(),
                            log.getCreatedAt(),
                            log.getActorUserId(),
                            actorNames.getOrDefault(log.getActorUserId(), "Unknown"),
                            metadataString(metadata, "scope"),
                            metadataInt(metadata, "reindexedCount"),
                            metadataString(metadata, "model"),
                            metadataInt(metadata, "dimensions")
                    );
                })
                .toList();
    }

    private UserEntity requireAdmin(UUID actorUserId) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin role required");
        }
        return actor;
    }

    private static final String EMBEDDINGS_REINDEXED_ACTION = "POST_EMBEDDINGS_REINDEXED";

    private String stringDetail(Health health, String key) {
        Object value = health.getDetails().get(key);
        return value == null ? null : value.toString();
    }

    private int integerDetail(Health health, String key) {
        Object value = health.getDetails().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private boolean booleanDetail(Health health, String key) {
        Object value = health.getDetails().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private Boolean booleanDetailNullable(Health health, String key) {
        Object value = health.getDetails().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String metadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    private int metadataInt(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return 0;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
