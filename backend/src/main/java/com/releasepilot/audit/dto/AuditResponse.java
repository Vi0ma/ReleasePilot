package com.releasepilot.audit.dto;
import com.releasepilot.audit.entity.AuditAction;
import com.releasepilot.audit.entity.AuditEvent;
import java.time.Instant;
import java.util.Map;

public record AuditResponse(Long id, Long actorUserId, Long projectId, AuditAction action,
                            String entityType, Long entityId, Map<String, Object> metadata, Instant createdAt) {
    public static AuditResponse from(AuditEvent event) {
        return new AuditResponse(event.getId(), event.getActorUserId(), event.getProjectId(), event.getAction(),
                event.getEntityType(), event.getEntityId(), event.getMetadata(), event.getCreatedAt());
    }
}
