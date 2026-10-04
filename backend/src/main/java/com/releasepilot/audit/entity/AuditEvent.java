package com.releasepilot.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_user_id", nullable = false, updatable = false) private Long actorUserId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private Long ownerUserId;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40) private AuditAction action;
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40) private String entityType;
    @Column(name = "entity_id", nullable = false, updatable = false) private Long entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb") private Map<String, Object> metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AuditEvent() { }
    public AuditEvent(Long actorUserId, Long ownerUserId, Long projectId, AuditAction action,
                      String entityType, Long entityId, Map<String, Object> metadata) {
        this.actorUserId = actorUserId; this.ownerUserId = ownerUserId; this.projectId = projectId;
        this.action = action; this.entityType = entityType; this.entityId = entityId;
        this.metadata = Map.copyOf(metadata);
    }
    @PrePersist private void initializeTimestamp() { createdAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public Long getProjectId() { return projectId; }
    public AuditAction getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
}
