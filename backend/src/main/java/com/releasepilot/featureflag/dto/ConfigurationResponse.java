package com.releasepilot.featureflag.dto;
import com.releasepilot.featureflag.entity.FlagConfiguration;
import java.time.Instant;

public record ConfigurationResponse(Long id, Long featureFlagId, Long environmentId, boolean enabled,
                                    int rolloutPercentage, Instant updatedAt) {
    public static ConfigurationResponse from(FlagConfiguration c) {
        return new ConfigurationResponse(c.getId(), c.getFeatureFlag().getId(), c.getEnvironment().getId(),
                c.isEnabled(), c.getRolloutPercentage(), c.getUpdatedAt());
    }
}
