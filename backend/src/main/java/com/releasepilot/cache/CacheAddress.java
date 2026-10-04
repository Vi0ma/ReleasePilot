package com.releasepilot.cache;

public record CacheAddress(Long environmentId, String flagKey) {
    public CacheKey atRevision(long revision) { return new CacheKey(environmentId, revision, flagKey); }
}
