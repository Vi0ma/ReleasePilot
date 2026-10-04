package com.releasepilot.apikey.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.releasepilot.environment.entity.Environment;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "environment_api_keys")
public class ApiKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;
    @Column(nullable = false, updatable = false, length = 100)
    private String name;
    @Column(name = "public_id", nullable = false, updatable = false, length = 32)
    private String publicId;
    @Column(name = "secret_hash", nullable = false, updatable = false, length = 64)
    private String secretHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() { }
    public ApiKey(Environment environment, String name, String publicId, String secretHash) {
        this.environment = environment;
        this.name = name;
        this.publicId = publicId;
        this.secretHash = secretHash;
    }
    @PrePersist private void initializeTimestamp() { createdAt = Instant.now(); }
    public void revoke() { if (revokedAt == null) { revokedAt = Instant.now(); } }
    public Long getId() { return id; }
    public Environment getEnvironment() { return environment; }
    public String getName() { return name; }
    public String getPublicId() { return publicId; }
    @JsonIgnore public String getSecretHash() { return secretHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
