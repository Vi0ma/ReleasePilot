package com.releasepilot.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasepilot.evaluation.domain.EvaluationConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisConfigurationCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CacheKey key = new CacheKey(42L, 3, "checkout");
    private RedisConfigurationCache cache() {
        when(redis.opsForValue()).thenReturn(values);
        return new RedisConfigurationCache(redis, new ObjectMapper(), 60);
    }
    @Test void decodesOnlyCompleteValidEvaluationConfigurations() {
        var cache = cache();
        when(values.get(key.redisKey())).thenReturn("{\"enabled\":true,\"rolloutPercentage\":37}");
        assertThat(cache.get(key)).contains(new EvaluationConfiguration(true, 37));
        for (String invalid : new String[]{"{}", "null", "{", "{\"enabled\":true}", "{\"enabled\":true,\"rolloutPercentage\":101}",
                "{\"enabled\":\"true\",\"rolloutPercentage\":50}", "{\"enabled\":true,\"rolloutPercentage\":1.5}"}) {
            when(values.get(key.redisKey())).thenReturn(invalid);
            assertThat(cache.get(key)).isEmpty();
        }
    }
    @Test void readFailureBecomesCacheMiss() {
        var cache = cache();
        when(values.get(key.redisKey())).thenThrow(new RedisConnectionFailureException("test outage"));
        assertThat(cache.get(key)).isEmpty();
    }
    @Test void writesWithTtlAndDoesNotPropagateRedisWriteOrInvalidationFailures() {
        var cache = cache();
        cache.put(key, new EvaluationConfiguration(false, 0));
        verify(values).set(key.redisKey(), "{\"enabled\":false,\"rolloutPercentage\":0}", Duration.ofSeconds(60));
        doThrow(new RedisConnectionFailureException("test outage")).when(values)
                .set(key.redisKey(), "{\"enabled\":false,\"rolloutPercentage\":0}", Duration.ofSeconds(60));
        when(redis.delete(key.redisKey())).thenThrow(new RedisConnectionFailureException("test outage"));
        assertThatCode(() -> cache.put(key, new EvaluationConfiguration(false, 0))).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict(key)).doesNotThrowAnyException();
    }
    @Test void ttlMustBeBounded() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisConfigurationCache(redis, new ObjectMapper(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisConfigurationCache(redis, new ObjectMapper(), 3601));
    }
}
