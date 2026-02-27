package io.github.eduardopaim.graphcognitio.admin.service;

import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
import io.github.eduardopaim.graphcognitio.admin.mapper.AdminUserMapper;
import io.github.eduardopaim.graphcognitio.audit.service.AuditLogService;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.exception.ForbiddenException;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AdminUserMapper adminUserMapper;
    private final AuditLogService auditLogService;

    public AdminService(UserRepository userRepository,
                        AdminUserMapper adminUserMapper,
                        AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.adminUserMapper = adminUserMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AdminUserResponse updateUserRole(UUID actorUserId, UUID targetUserId, UserRole newRole) {
        UserEntity actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin role required");
        }

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
}
