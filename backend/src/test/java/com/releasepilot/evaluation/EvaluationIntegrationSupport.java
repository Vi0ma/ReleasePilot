package com.releasepilot.evaluation;

import com.fasterxml.jackson.databind.*;
import com.releasepilot.project.service.ProjectService;
import com.releasepilot.security.JwtService;
import com.releasepilot.user.entity.*;
import com.releasepilot.user.repository.UserRepository;
import com.releasepilot.cache.CacheKey;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class EvaluationIntegrationSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected JwtService jwt;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected ProjectService projects;
    @Autowired protected EntityManager entityManager;
    protected Long ownerId, otherId;
    protected String token, otherToken, apiKey;
    protected long project, development, production, flag, apiKeyId;
    protected final Set<String> touchedCacheKeys = new HashSet<>();

    @BeforeEach void fixtures() throws Exception {
        ownerId = users.saveAndFlush(new User("Evaluation Owner", email(), "test-hash-placeholder", UserRole.USER)).getId();
        otherId = users.saveAndFlush(new User("Evaluation Other", email(), "test-hash-placeholder", UserRole.USER)).getId();
        token = jwt.generate(ownerId);
        otherToken = jwt.generate(otherId);
        project = body(send("POST", token, "/api/v1/projects", Map.of("name", "Evaluation test", "key", "evaluation-test"))
                .andExpect(status().isCreated())).get("id").asLong();
        var environments = body(getJwt(token, "/api/v1/projects/" + project + "/environments"));
        for (var environment : environments) {
            if (environment.get("key").asText().equals("development")) { development = environment.get("id").asLong(); }
            if (environment.get("key").asText().equals("production")) { production = environment.get("id").asLong(); }
        }
        flag = body(send("POST", token, "/api/v1/projects/" + project + "/flags",
                Map.of("name", "Checkout", "key", "new_checkout")).andExpect(status().isCreated())).get("id").asLong();
        var key = body(createKey(development).andExpect(status().isCreated()));
        apiKey = key.get("apiKey").asText();
        apiKeyId = key.at("/key/id").asLong();
    }
    @AfterEach void cleanupOnlyFixtureData() {
        if (ownerId == null || otherId == null) { return; }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var fixtures = jdbc.queryForList("select id, owner_id from projects where owner_id in (?, ?)", ownerId, otherId);
            for (var fixture : fixtures) {
                projects.delete(((Number) fixture.get("owner_id")).longValue(), ((Number) fixture.get("id")).longValue());
            }
            jdbc.update("delete from audit_events where owner_user_id in (?, ?)", ownerId, otherId);
            jdbc.update("delete from users where id in (?, ?)", ownerId, otherId);
            entityManager.clear();
        });
        if (!touchedCacheKeys.isEmpty()) { redis.delete(touchedCacheKeys); }
    }
    protected ResultActions createKey(long environment) throws Exception {
        return send("POST", token, keyPath(environment), Map.of("name", "Test application"));
    }
    protected String keyPath(long environment) {
        return "/api/v1/projects/" + project + "/environments/" + environment + "/api-keys";
    }
    protected void configure(long environment, boolean enabled, int percentage) throws Exception {
        send("PATCH", token, "/api/v1/flags/" + flag + "/environments/" + environment,
                Map.of("enabled", enabled, "rolloutPercentage", percentage)).andExpect(status().isOk());
    }
    protected CacheKey currentKey() {
        long revision = jdbc.queryForObject("select evaluation_revision from projects where id = ?", Long.class, project);
        var key = new CacheKey(development, revision, "new_checkout");
        touchedCacheKeys.add(key.redisKey());
        return key;
    }
    protected ResultActions evaluate(String key, String flagKey, String userKey) throws Exception {
        return mvc.perform(post("/api/v1/evaluate").header("X-API-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("flagKey", flagKey, "userKey", userKey))));
    }
    protected ResultActions getJwt(String jwtToken, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + jwtToken));
    }
    protected ResultActions send(String method, String jwtToken, String path, Object data) throws Exception {
        var request = switch (method) {
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        request.header("Authorization", "Bearer " + jwtToken);
        if (data != null) { request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)); }
        return mvc.perform(request);
    }
    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private String email() { return "evaluation-" + UUID.randomUUID() + "@example.test"; }
}
