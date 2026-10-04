package com.releasepilot.featureflag.dto;
import com.releasepilot.featureflag.entity.FeatureFlag;
import java.time.Instant;

public record FlagResponse(Long id, Long projectId, String key, String name, String description,
                           Instant createdAt, Instant updatedAt) {
    public static FlagResponse from(FeatureFlag f) {
        return new FlagResponse(f.getId(), f.getProject().getId(), f.getKey(), f.getName(),
                f.getDescription(), f.getCreatedAt(), f.getUpdatedAt());
    }
}
