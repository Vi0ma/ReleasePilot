package com.releasepilot.featureflag.entity;

import jakarta.persistence.*;
import java.time.Instant;
import com.releasepilot.environment.entity.Environment;

@Entity
@Table(name = "flag_configurations")
public class FlagConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_flag_id", nullable = false, updatable = false) private FeatureFlag featureFlag;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false) private Environment environment;
    @Column(name = "project_id", nullable = false, updatable = false) private Long projectId;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "rollout_percentage", nullable = false) private int rolloutPercentage;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @PrePersist @PreUpdate private void updateTimestamp() { updatedAt = Instant.now(); }
    public Instant getUpdatedAt() { return updatedAt; }

    protected FlagConfiguration() { }
    public FlagConfiguration(FeatureFlag featureFlag, Environment environment) {
        if (!featureFlag.getProject().getId().equals(environment.getProject().getId())) {
            throw new IllegalArgumentException("Flag and environment must belong to the same project");
        }
        this.featureFlag = featureFlag; this.environment = environment;
        this.projectId = featureFlag.getProject().getId();
        this.enabled = false; this.rolloutPercentage = 0;
    }
    public void update(boolean enabled, int rolloutPercentage) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("Rollout percentage must be between 0 and 100");
        }
        this.enabled = enabled; this.rolloutPercentage = rolloutPercentage;
    }
    public Long getId() { return id; }
    public FeatureFlag getFeatureFlag() { return featureFlag; }
    public Environment getEnvironment() { return environment; }
    public boolean isEnabled() { return enabled; }
    public int getRolloutPercentage() { return rolloutPercentage; }
}
