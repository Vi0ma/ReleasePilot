package com.releasepilot.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasepilot.evaluation.domain.EvaluationConfiguration;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisConfigurationCache implements ConfigurationCache {
    private static final Logger log = LoggerFactory.getLogger(RedisConfigurationCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;
    public RedisConfigurationCache(StringRedisTemplate redis, ObjectMapper json,
                                   @Value("${releasepilot.evaluation.cache-ttl-seconds}") long ttlSeconds) {
        if (ttlSeconds < 1 || ttlSeconds > 3600) { throw new IllegalArgumentException("Cache TTL must be 1..3600 seconds"); }
        this.redis = redis;
        this.json = json;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }
    @Override
    public Optional<EvaluationConfiguration> get(CacheKey key) {
        try {
            String value = redis.opsForValue().get(key.redisKey());
            if (value == null) { return Optional.empty(); }
            if (value.length() > 128) { return Optional.empty(); }
            var node = json.readTree(value);
            if (node == null || !node.isObject() || node.size() != 2 || !node.path("enabled").isBoolean()
                    || !node.path("rolloutPercentage").isIntegralNumber() || !node.path("rolloutPercentage").canConvertToInt()
                    || node.path("rolloutPercentage").intValue() < 0 || node.path("rolloutPercentage").intValue() > 100) {
                log.warn("Invalid evaluation cache entry; using PostgreSQL");
                return Optional.empty();
            }
            return Optional.of(new EvaluationConfiguration(node.get("enabled").asBoolean(),
                    node.get("rolloutPercentage").asInt()));
        } catch (DataAccessException | JsonProcessingException ex) {
            log.warn("Evaluation cache read unavailable; using PostgreSQL");
            return Optional.empty();
        }
    }
    @Override
    public void put(CacheKey key, EvaluationConfiguration configuration) {
        try { redis.opsForValue().set(key.redisKey(), json.writeValueAsString(configuration), ttl); }
        catch (DataAccessException | JsonProcessingException ex) { log.warn("Evaluation cache write unavailable"); }
    }
    @Override
    public void evict(CacheKey key) {
        try { redis.delete(key.redisKey()); }
        catch (DataAccessException ex) { log.warn("Evaluation cache invalidation unavailable; PostgreSQL revision remains authoritative"); }
    }
}
