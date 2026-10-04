package com.releasepilot.evaluation;

import com.releasepilot.evaluation.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=100ms", "spring.data.redis.timeout=100ms"})
@AutoConfigureMockMvc
class RedisFallbackIntegrationTest extends EvaluationIntegrationSupport {
    @Test void unavailableRedisReturnsTheSameDecisionUsingPostgresql() throws Exception {
        configure(development, true, 61);
        var expected = new RolloutEngine(new StableBucketHasher())
                .evaluate(new EvaluationConfiguration(true, 61), "user-123", "new_checkout");
        evaluate(apiKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(expected.enabled())).andExpect(jsonPath("reason").value(expected.reason().name()));
        configure(development, false, 0);
        evaluate(apiKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("reason").value("FLAG_DISABLED"));
    }
}
