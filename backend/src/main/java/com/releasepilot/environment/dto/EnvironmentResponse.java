package com.releasepilot.environment.dto;
import com.releasepilot.environment.entity.Environment;
import java.time.Instant;

public record EnvironmentResponse(Long id, Long projectId, String name, String key, Instant createdAt) {
    public static EnvironmentResponse from(Environment e) {
        return new EnvironmentResponse(e.getId(), e.getProject().getId(), e.getName(), e.getKey(), e.getCreatedAt());
    }
}
