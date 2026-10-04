package com.releasepilot.featureflag.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.releasepilot.project.entity.Project;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false) private Project project;
    @Column(name = "flag_key", nullable = false, updatable = false, length = 100) private String key;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 2000) private String description;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @PrePersist private void initializeTimestamps() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate private void updateTimestamp() { updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    protected FeatureFlag() { }
    public FeatureFlag(Project project, String key, String name, String description) {
        this.project = project; this.key = key; this.name = name; this.description = description;
    }
    public void update(String name, String description) { this.name = name; this.description = description; }
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
