package com.releasepilot.cache;

public record CacheKey(Long environmentId, long revision, String flagKey) {
    public String redisKey() { return "flag-config:v1:" + environmentId + ":" + revision + ":" + flagKey; }
}
