package com.releasepilot.environment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.releasepilot.project.entity.Project;

@Entity
@Table(name = "environments")
public class Environment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false) private Project project;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "environment_key", nullable = false, updatable = false, length = 100) private String key;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist private void initializeTimestamp() { createdAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }

    protected Environment() { }
    public Environment(Project project, String name, String key) {
        this.project = project; this.name = name; this.key = key;
    }
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getName() { return name; }
    public String getKey() { return key; }
}
