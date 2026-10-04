package com.releasepilot.project.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.releasepilot.user.entity.User;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "project_key", nullable = false, updatable = false, length = 100) private String key;
    @Column(length = 2000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false) private User owner;
    @Column(name = "evaluation_revision", nullable = false)
    private long evaluationRevision;
    public long getEvaluationRevision() { return evaluationRevision; }
    public void advanceEvaluationRevision() { evaluationRevision = Math.incrementExact(evaluationRevision); }
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @PrePersist private void initializeTimestamps() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate private void updateTimestamp() { updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    protected Project() { }
    public Project(String name, String key, String description, User owner) {
        this.name = name; this.key = key; this.description = description; this.owner = owner;
    }
    public void update(String name, String description) { this.name = name; this.description = description; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getKey() { return key; }
    public String getDescription() { return description; }
    public User getOwner() { return owner; }
}
