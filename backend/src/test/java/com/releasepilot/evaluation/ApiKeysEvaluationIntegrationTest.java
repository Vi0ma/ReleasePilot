package com.releasepilot.evaluation;

import com.releasepilot.cache.CacheAddress;
import com.releasepilot.cache.CacheKey;
import com.releasepilot.featureflag.dto.UpdateConfigurationRequest;
import com.releasepilot.featureflag.service.FlagConfigurationService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeysEvaluationIntegrationTest extends EvaluationIntegrationSupport {
    @Autowired private FlagConfigurationService configurations;

    @Test void generatedKeysAreHashedOnlyShownOnceAndAuditedOnRevocation() throws Exception {
        String hash = jdbc.queryForObject("select secret_hash from environment_api_keys where id = ?", String.class, apiKeyId);
        assertThat(hash).hasSize(64).isNotEqualTo(apiKey).isNotEqualTo(apiKey.split("\\.")[1]);
        var second = createKey(development).andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"));
        String secondSecret = body(second).get("apiKey").asText();
        var listed = getJwt(token, keyPath(development)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain(apiKey, secondSecret, hash, "secretHash", "secret_hash", "apiKey\"");
        send("DELETE", token, keyPath(development) + "/" + apiKeyId, null).andExpect(status().isNoContent());
        send("DELETE", token, keyPath(development) + "/" + apiKeyId, null).andExpect(status().isNoContent());
        evaluate(apiKey, "new_checkout", "user").andExpect(status().isUnauthorized());
        evaluate(secondSecret, "new_checkout", "user").andExpect(status().isOk());
        var list = body(getJwt(token, keyPath(development)));
        assertThat(list.get(0).get("revokedAt").isNull()).isFalse();
        var audit = body(getJwt(token, "/api/v1/projects/" + project + "/audit"));
        long revoked = 0, created = 0;
        for (var event : audit.get("content")) {
            if (event.get("action").asText().equals("API_KEY_CREATED")) { created++; }
            if (event.get("action").asText().equals("API_KEY_REVOKED")) { revoked++; }
        }
        assertThat(created).isEqualTo(2);
        assertThat(revoked).isEqualTo(1);
        assertThat(audit.toString()).doesNotContain(apiKey, secondSecret, hash, "secretHash");
        touchedCacheKeys.add(currentKey().redisKey());
    }

    @Test void apiKeysAndJwtCannotSubstituteForEachOtherAndWrongSecretsFail() throws Exception {
        mvc.perform(post("/api/v1/evaluate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"flagKey\":\"new_checkout\",\"userKey\":\"user\"}")).andExpect(status().isUnauthorized());
        send("POST", token, "/api/v1/evaluate", Map.of("flagKey", "new_checkout", "userKey", "user"))
                .andExpect(status().isUnauthorized());
        evaluate("invalid", "new_checkout", "user").andExpect(status().isUnauthorized());
        String wrong = apiKey.substring(0, apiKey.indexOf('.') + 1) + "A".repeat(43);
        evaluate(wrong, "new_checkout", "user").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/evaluate").header("X-API-Key", apiKey, apiKey)
                .contentType(MediaType.APPLICATION_JSON).content("{\"flagKey\":\"new_checkout\",\"userKey\":\"user\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects").header("X-API-Key", apiKey)).andExpect(status().isUnauthorized());
        getJwt(otherToken, keyPath(development)).andExpect(status().isNotFound());
        send("POST", otherToken, keyPath(development), Map.of("name", "Forbidden")).andExpect(status().isNotFound());
        send("DELETE", otherToken, keyPath(development) + "/" + apiKeyId, null).andExpect(status().isNotFound());
        send("DELETE", token, keyPath(production) + "/" + apiKeyId, null).andExpect(status().isNotFound());
    }

    @Test void evaluatesReasonsAndIsolatesEnvironmentsAndProjectsWithoutLeakingData() throws Exception {
        String productionKey = body(createKey(production)).get("apiKey").asText();
        evaluate(apiKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("reason").value("FLAG_DISABLED"));
        configure(development, true, 100);
        var response = evaluate(apiKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("reason").value("ROLLOUT_MATCH"));
        assertThat(body(response).size()).isEqualTo(3);
        assertThat(body(response).toString()).doesNotContain(apiKey, "environmentId", "passwordHash", "secretHash", "JWT_SECRET", "rolloutPercentage", "bucket");
        evaluate(productionKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("reason").value("FLAG_DISABLED"));
        configure(development, true, 0);
        evaluate(apiKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("reason").value("ROLLOUT_MISS"));
        configure(development, true, 61);
        evaluate(apiKey, "new_checkout", "user-123").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("reason").value("ROLLOUT_MATCH"));
        long foreign = body(send("POST", otherToken, "/api/v1/projects", Map.of("name", "Other", "key", "other")))
                .get("id").asLong();
        send("POST", otherToken, "/api/v1/projects/" + foreign + "/flags", Map.of("name", "Foreign", "key", "foreign_only"))
                .andExpect(status().isCreated());
        evaluate(apiKey, "foreign_only", "user").andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("reason").value("FLAG_NOT_FOUND"));
        currentKey();
        long revision = jdbc.queryForObject("select evaluation_revision from projects where id = ?", Long.class, project);
        touchedCacheKeys.add(new CacheKey(production, revision, "new_checkout").redisKey());
    }

    @Test void invalidInputAndClientEnvironmentSelectionAreRejected() throws Exception {
        mvc.perform(post("/api/v1/evaluate").header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"flagKey\":\"new_checkout\",\"userKey\":\"u\",\"environmentId\":" + production + "}"))
                .andExpect(status().isBadRequest());
        evaluate(apiKey, "INVALID KEY", "user").andExpect(status().isBadRequest());
        evaluate(apiKey, "new_checkout", " ").andExpect(status().isBadRequest());
        evaluate(apiKey, "new_checkout", "a".repeat(257)).andExpect(status().isBadRequest());
        send("POST", token, keyPath(development), Map.of("name", " ")).andExpect(status().isBadRequest());
    }

    @Test void cacheMissPopulatesRedisHitPreservesTtlAndUpdatesInvalidateAfterCommit() throws Exception {
        configure(development, true, 100);
        var key = currentKey();
        assertThat(redis.hasKey(key.redisKey())).isFalse();
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(true));
        String cached = redis.opsForValue().get(key.redisKey());
        assertThat(json.readTree(cached).size()).isEqualTo(2);
        assertThat(json.readTree(cached).get("rolloutPercentage").asInt()).isEqualTo(100);
        assertThat(redis.getExpire(key.redisKey())).isBetween(1L, 60L);
        redis.expire(key.redisKey(), Duration.ofSeconds(10));
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(true));
        assertThat(redis.getExpire(key.redisKey())).isBetween(1L, 10L);
        configure(development, false, 0);
        assertThat(redis.hasKey(key.redisKey())).isFalse();
        var next = currentKey();
        assertThat(next.revision()).isGreaterThan(key.revision());
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("reason").value("FLAG_DISABLED"));
        assertThat(redis.hasKey(next.redisKey())).isTrue();
    }

    @Test void staleRefillsCannotUndoUpdatesOrFlagDeletion() throws Exception {
        configure(development, true, 100);
        var old = currentKey();
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(true));
        String oldValue = redis.opsForValue().get(old.redisKey());
        configure(development, false, 0);
        redis.opsForValue().set(old.redisKey(), oldValue, Duration.ofSeconds(60));
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(false));
        var beforeDelete = currentKey();
        send("DELETE", token, "/api/v1/projects/" + project + "/flags/" + flag, null).andExpect(status().isNoContent());
        assertThat(redis.hasKey(beforeDelete.redisKey())).isFalse();
        redis.opsForValue().set(beforeDelete.redisKey(), oldValue, Duration.ofSeconds(60));
        evaluate(apiKey, "new_checkout", "user").andExpect(status().isOk())
                .andExpect(jsonPath("reason").value("FLAG_NOT_FOUND")).andExpect(jsonPath("enabled").value(false));
    }

    @Test void projectDeletionRemovesApiKeysAndCacheButRetainsAudit() throws Exception {
        configure(development, true, 100);
        var key = currentKey();
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(true));
        send("DELETE", token, "/api/v1/projects/" + project, null).andExpect(status().isNoContent());
        assertThat(redis.hasKey(key.redisKey())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from environment_api_keys where id = ?", Long.class, apiKeyId)).isZero();
        evaluate(apiKey, "new_checkout", "user").andExpect(status().isUnauthorized());
        getJwt(token, "/api/v1/projects/" + project + "/audit").andExpect(status().isOk());
    }

    @Test void rollbackPreservesThePreviousCacheAndPostgresqlRevision() throws Exception {
        configure(development, true, 100);
        var key = currentKey();
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(true));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            configurations.update(ownerId, flag, development, new UpdateConfigurationRequest(false, 0));
            assertThat(redis.hasKey(key.redisKey())).isTrue();
            status.setRollbackOnly();
        });
        assertThat(currentKey()).isEqualTo(key);
        assertThat(redis.hasKey(key.redisKey())).isTrue();
        evaluate(apiKey, "new_checkout", "user").andExpect(jsonPath("enabled").value(true));
    }
}
