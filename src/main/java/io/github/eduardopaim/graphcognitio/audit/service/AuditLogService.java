package io.github.eduardopaim.graphcognitio.audit.service;

import io.github.eduardopaim.graphcognitio.entity.AuditLogEntity;
import io.github.eduardopaim.graphcognitio.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(UUID actorUserId,
                    String action,
                    String entityType,
                    UUID entityId,
                    Map<String, Object> metadata) {
        AuditLogEntity auditLog = new AuditLogEntity();
        auditLog.setActorUserId(actorUserId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setMetadata(metadata);
        auditLogRepository.save(auditLog);
    }
}
