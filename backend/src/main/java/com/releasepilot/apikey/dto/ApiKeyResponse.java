package com.releasepilot.apikey.dto;
import com.releasepilot.apikey.entity.ApiKey;
import java.time.Instant;

public record ApiKeyResponse(Long id, Long environmentId, String name, String prefix,
                             Instant createdAt, Instant revokedAt) {
    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getEnvironment().getId(), key.getName(),
                "rp_" + key.getPublicId(), key.getCreatedAt(), key.getRevokedAt());
    }
}
