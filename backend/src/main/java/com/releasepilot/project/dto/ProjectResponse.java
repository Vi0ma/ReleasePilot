package com.releasepilot.project.dto;
import com.releasepilot.project.entity.Project;
import java.time.Instant;

public record ProjectResponse(Long id, String name, String key, String description,
                              Long ownerId, Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getName(), p.getKey(), p.getDescription(),
                p.getOwner().getId(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
