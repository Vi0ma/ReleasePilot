package com.releasepilot.cache;

import com.releasepilot.evaluation.domain.EvaluationConfiguration;
import java.util.Optional;

public interface ConfigurationCache {
    Optional<EvaluationConfiguration> get(CacheKey key);
    void put(CacheKey key, EvaluationConfiguration configuration);
    void evict(CacheKey key);
}
