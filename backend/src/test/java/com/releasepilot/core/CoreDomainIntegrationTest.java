package com.releasepilot.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasepilot.security.JwtService;
import com.releasepilot.featureflag.entity.FlagConfiguration;
import com.releasepilot.featureflag.service.FeatureFlagService;
import com.releasepilot.project.entity.Project;
import com.releasepilot.project.service.ProjectService;
import com.releasepilot.user.entity.*;
import com.releasepilot.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoreDomainIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private EntityManager entityManager;
    @Autowired private FeatureFlagService flagService;
    @Autowired private ProjectService projectService;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void usersAndTokens() {
        var owner = users.saveAndFlush(new User("Owner", email(), "test-hash-placeholder", UserRole.USER));
        var other = users.saveAndFlush(new User("Other", email(), "test-hash-placeholder", UserRole.USER));
        ownerToken = jwt.generate(owner.getId());
        otherToken = jwt.generate(other.getId());
    }

    @Test
    void projectAndFlagLifecycleRecordsAllActionsAndPreservesAuditAfterDeletion() throws Exception {
        assertThat(flyway.info().applied()).anySatisfy(migration -> {
            assertThat(migration.getVersion().getVersion()).isEqualTo("3");
            assertThat(migration.getScript()).isEqualTo("V3__create_core_domain.sql");
        });
        long project = createProject(ownerToken, "demo-store");
        var environments = body(getRequest(ownerToken, envPath(project)).andExpect(status().isOk()));
        assertThat(environments.size()).isEqualTo(3);
        Set<String> environmentKeys = new HashSet<>();
        environments.forEach(env -> environmentKeys.add(env.get("key").asText()));
        assertThat(environmentKeys).containsExactlyInAnyOrder("development", "staging", "production");
        long environment = environments.get(0).get("id").asLong();

        getRequest(ownerToken, projectPath(project)).andExpect(status().isOk()).andExpect(jsonPath("key").value("demo-store"));
        send("PUT", ownerToken, projectPath(project), Map.of("name", "Renamed project", "description", "Updated"))
                .andExpect(status().isOk()).andExpect(jsonPath("key").value("demo-store"));
        long flag = createFlag(ownerToken, project, "new_checkout");
        getRequest(ownerToken, flagsPath(project)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        getRequest(ownerToken, flagPath(project, flag)).andExpect(status().isOk());
        var configurations = body(getRequest(ownerToken, flagPath(project, flag) + "/configurations").andExpect(status().isOk()));
        assertThat(configurations.size()).isEqualTo(3);
        configurations.forEach(configuration -> {
            assertThat(configuration.get("enabled").asBoolean()).isFalse();
            assertThat(configuration.get("rolloutPercentage").asInt()).isZero();
        });
        send("PUT", ownerToken, flagPath(project, flag), Map.of("name", "Checkout V2", "description", "New checkout"))
                .andExpect(status().isOk()).andExpect(jsonPath("key").value("new_checkout"));
        send("PATCH", ownerToken, configPath(flag, environment), Map.of("enabled", true, "rolloutPercentage", 25))
                .andExpect(status().isOk()).andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("rolloutPercentage").value(25));
        send("DELETE", ownerToken, flagPath(project, flag), null).andExpect(status().isNoContent());
        getRequest(ownerToken, flagPath(project, flag)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from flag_configurations where feature_flag_id = ?", Long.class, flag)).isZero();
        send("DELETE", ownerToken, projectPath(project), null).andExpect(status().isNoContent());
        getRequest(ownerToken, projectPath(project)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from environments where project_id = ?", Long.class, project)).isZero();

        var auditResult = getRequest(ownerToken, auditPath(project)).andExpect(status().isOk()).andReturn();
        var audit = json.readTree(auditResult.getResponse().getContentAsString());
        Set<String> actions = new HashSet<>();
        audit.get("content").forEach(event -> {
            actions.add(event.get("action").asText());
            assertThat(event.get("projectId").asLong()).isEqualTo(project);
            if (event.get("action").asText().equals("FLAG_CONFIGURATION_UPDATED")) {
                assertThat(event.at("/metadata/before/enabled").asBoolean()).isFalse();
                assertThat(event.at("/metadata/after/enabled").asBoolean()).isTrue();
            }
        });
        assertThat(actions).containsExactlyInAnyOrder("PROJECT_CREATED", "PROJECT_UPDATED", "PROJECT_DELETED",
                "FLAG_CREATED", "FLAG_UPDATED", "FLAG_DELETED", "FLAG_CONFIGURATION_UPDATED");
        assertThat(audit.get("totalElements").asLong()).isEqualTo(7);
        assertThat(auditResult.getResponse().getContentAsString()).doesNotContain("passwordHash", "password_hash");
        getRequest(otherToken, auditPath(project)).andExpect(status().isNotFound());
        getRequest(ownerToken, auditPath(project) + "?size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("totalElements").value(7));
        getRequest(ownerToken, auditPath(project) + "?size=101").andExpect(status().isBadRequest());
    }

    @Test
    void ownershipAndParentIsolationApplyToEveryResource() throws Exception {
        long owned = createProject(ownerToken, "owned");
        long other = createProject(otherToken, "other");
        long flag = createFlag(ownerToken, owned, "checkout");
        long env = body(getRequest(ownerToken, envPath(owned))).get(0).get("id").asLong();
        getRequest(ownerToken, "/api/v1/projects").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(owned));
        getRequest(otherToken, "/api/v1/projects").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other));

        getRequest(otherToken, projectPath(owned)).andExpect(status().isNotFound());
        send("PUT", otherToken, projectPath(owned), Map.of("name", "Forbidden")).andExpect(status().isNotFound());
        send("DELETE", otherToken, projectPath(owned), null).andExpect(status().isNotFound());
        getRequest(otherToken, envPath(owned)).andExpect(status().isNotFound());
        getRequest(otherToken, envPath(owned) + "/" + env).andExpect(status().isNotFound());
        getRequest(otherToken, envPath(other) + "/" + env).andExpect(status().isNotFound());
        getRequest(otherToken, flagsPath(owned)).andExpect(status().isNotFound());
        send("POST", otherToken, flagsPath(owned), Map.of("name", "Forbidden", "key", "forbidden")).andExpect(status().isNotFound());
        getRequest(otherToken, flagPath(owned, flag)).andExpect(status().isNotFound());
        getRequest(otherToken, flagPath(other, flag)).andExpect(status().isNotFound());
        send("PUT", otherToken, flagPath(owned, flag), Map.of("name", "Forbidden")).andExpect(status().isNotFound());
        send("DELETE", otherToken, flagPath(owned, flag), null).andExpect(status().isNotFound());
        getRequest(otherToken, flagPath(owned, flag) + "/configurations").andExpect(status().isNotFound());
        getRequest(otherToken, configPath(flag, env)).andExpect(status().isNotFound());
        send("PATCH", otherToken, configPath(flag, env), Map.of("enabled", true, "rolloutPercentage", 100))
                .andExpect(status().isNotFound());
        getRequest(otherToken, auditPath(owned)).andExpect(status().isNotFound());
        getRequest(ownerToken, projectPath(owned)).andExpect(status().isOk()).andExpect(jsonPath("name").value("Test project"));
        getRequest(ownerToken, configPath(flag, env)).andExpect(status().isOk()).andExpect(jsonPath("enabled").value(false));
        getRequest(ownerToken, auditPath(owned)).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
    }

    @Test
    void keysAreScopedAndDuplicateOperationsHaveNoPartialEffects() throws Exception {
        long first = createProject(ownerToken, "store");
        long second = createProject(ownerToken, "another-store");
        createProject(otherToken, "store");
        send("POST", ownerToken, "/api/v1/projects", Map.of("name", "Duplicate", "key", "store"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("DUPLICATE_PROJECT_KEY"));
        long flag = createFlag(ownerToken, first, "checkout");
        createFlag(ownerToken, second, "checkout");
        send("POST", ownerToken, flagsPath(first), Map.of("name", "Duplicate", "key", "checkout"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("DUPLICATE_FLAG_KEY"));
        getRequest(ownerToken, flagsPath(first)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        getRequest(ownerToken, flagPath(first, flag) + "/configurations").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        getRequest(ownerToken, auditPath(first)).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
    }

    @Test
    void configurationSupportsOnOffAndBoundaryPercentagesRejectingInvalidValues() throws Exception {
        long project = createProject(ownerToken, "config");
        long otherProject = createProject(ownerToken, "config-other");
        long flag = createFlag(ownerToken, project, "checkout");
        long environment = body(getRequest(ownerToken, envPath(project))).get(0).get("id").asLong();
        long foreignEnvironment = body(getRequest(ownerToken, envPath(otherProject))).get(0).get("id").asLong();
        for (int percentage : new int[]{0, 50, 100}) {
            send("PATCH", ownerToken, configPath(flag, environment), Map.of("enabled", true, "rolloutPercentage", percentage))
                    .andExpect(status().isOk()).andExpect(jsonPath("enabled").value(true))
                    .andExpect(jsonPath("rolloutPercentage").value(percentage));
        }
        send("PATCH", ownerToken, configPath(flag, environment), Map.of("enabled", false, "rolloutPercentage", 100))
                .andExpect(status().isOk()).andExpect(jsonPath("enabled").value(false));
        for (int invalid : new int[]{-1, 101}) {
            send("PATCH", ownerToken, configPath(flag, environment), Map.of("enabled", true, "rolloutPercentage", invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        send("PATCH", ownerToken, configPath(flag, environment), Map.of("rolloutPercentage", 0)).andExpect(status().isBadRequest());
        send("PATCH", ownerToken, configPath(flag, foreignEnvironment), Map.of("enabled", true, "rolloutPercentage", 10))
                .andExpect(status().isNotFound());
        getRequest(ownerToken, configPath(flag, foreignEnvironment)).andExpect(status().isNotFound());
        getRequest(ownerToken, configPath(flag, environment)).andExpect(status().isOk())
                .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("rolloutPercentage").value(100));
        getRequest(ownerToken, auditPath(project)).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(6));
    }

    @Test
    void deletingProjectExplicitlyRemovesItsChildrenButKeepsOtherProjects() throws Exception {
        long first = createProject(ownerToken, "delete-me");
        long second = createProject(ownerToken, "keep-me");
        long firstFlag = createFlag(ownerToken, first, "checkout");
        long secondFlag = createFlag(ownerToken, second, "checkout");
        send("DELETE", ownerToken, projectPath(first), null).andExpect(status().isNoContent());
        for (String table : List.of("environments", "feature_flags", "flag_configurations")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where project_id = ?", Long.class, first)).isZero();
        }
        getRequest(ownerToken, flagPath(second, secondFlag)).andExpect(status().isOk());
        getRequest(ownerToken, configPath(firstFlag, 1L)).andExpect(status().isNotFound());
        getRequest(ownerToken, envPath(second)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        getRequest(ownerToken, auditPath(first)).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(3));
    }

    @Test
    void deletesHandleManagedChildrenAndPreservePendingChangesAndAudit() throws Exception {
        long deletedProject = createProject(ownerToken, "managed-delete");
        long keptProject = createProject(ownerToken, "managed-keep");
        long deletedFlag = createFlag(ownerToken, deletedProject, "remove-first");
        long remainingFlag = createFlag(ownerToken, deletedProject, "remove-with-project");
        long keptFlag = createFlag(ownerToken, keptProject, "keep");
        long actor = Long.parseLong(jwt.validate(ownerToken).getSubject());

        var managedConfigurations = entityManager.createQuery(
                "select c from FlagConfiguration c where c.projectId = :project", FlagConfiguration.class)
                .setParameter("project", deletedProject).getResultList();
        assertThat(managedConfigurations).hasSize(6);
        assertThat(entityManager.contains(managedConfigurations.getFirst())).isTrue();
        var pendingProject = entityManager.find(Project.class, keptProject);
        pendingProject.update("Pending change survives", "Flushed before clearing the context");

        // Direct service calls expose the original exception if a flush fails.
        flagService.delete(actor, deletedProject, deletedFlag);
        assertThat(jdbc.queryForObject("select count(*) from flag_configurations where feature_flag_id = ?",
                Long.class, deletedFlag)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from flag_configurations where feature_flag_id = ?",
                Long.class, remainingFlag)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select name from projects where id = ?",
                String.class, keptProject)).isEqualTo("Pending change survives");

        projectService.delete(actor, deletedProject);
        assertThat(entityManager.find(Project.class, deletedProject)).isNull();
        for (String table : List.of("environments", "feature_flags", "flag_configurations")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where project_id = ?",
                    Long.class, deletedProject)).isZero();
        }
        getRequest(ownerToken, flagPath(keptProject, keptFlag)).andExpect(status().isOk());
        getRequest(ownerToken, flagPath(keptProject, keptFlag) + "/configurations")
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        var audit = body(getRequest(ownerToken, auditPath(deletedProject)).andExpect(status().isOk()));
        assertThat(audit.get("totalElements").asLong()).isEqualTo(5);
        Set<String> actions = new HashSet<>();
        audit.get("content").forEach(event -> actions.add(event.get("action").asText()));
        assertThat(actions).contains("FLAG_DELETED", "PROJECT_DELETED");
        getRequest(otherToken, auditPath(deletedProject)).andExpect(status().isNotFound());
    }

    @Test
    void databaseConstraintsRejectInvalidRolloutAndCrossProjectConfiguration() throws Exception {
        long first = createProject(ownerToken, "constraints");
        long second = createProject(ownerToken, "constraints-other");
        long flag = createFlag(ownerToken, first, "checkout");
        long environment = body(getRequest(ownerToken, envPath(first))).get(0).get("id").asLong();
        long foreignEnvironment = body(getRequest(ownerToken, envPath(second))).get(0).get("id").asLong();
        long configuration = body(getRequest(ownerToken, configPath(flag, environment))).get("id").asLong();
        assertSqlRejected("update flag_configurations set rollout_percentage = 101 where id = ?", "23514", configuration);
        assertSqlRejected("update flag_configurations set environment_id = ? where id = ?", "23503", foreignEnvironment, configuration);
        assertSqlRejected("update environments set environment_key = 'INVALID KEY' where id = ?", "23514", environment);
        assertSqlRejected("update feature_flags set flag_key = 'INVALID KEY' where id = ?", "23514", flag);
    }

    @Test
    void jwtAndInputValidationAreRequired() throws Exception {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"key\":\"test\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/1/environments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/1/flags")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/flags/1/environments/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"rolloutPercentage\":10}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/1/audit")).andExpect(status().isUnauthorized());
        send("POST", ownerToken, "/api/v1/projects", Map.of("name", "", "key", "Bad Key")).andExpect(status().isBadRequest());
        long project = createProject(ownerToken, "valid");
        send("POST", ownerToken, flagsPath(project), Map.of("name", "Flag", "key", "INVALID KEY")).andExpect(status().isBadRequest());
    }

    private void assertSqlRejected(String sql, String state, Object... args) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) { statement.setObject(i + 1, args[i]); }
                try {
                    statement.executeUpdate();
                    fail("Expected SQL constraint violation: " + state);
                } catch (SQLException ex) {
                    assertThat(ex.getSQLState()).isEqualTo(state);
                }
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }
    private long createProject(String token, String key) throws Exception {
        return body(send("POST", token, "/api/v1/projects", Map.of("name", "Test project", "key", key))
                .andExpect(status().isCreated())).get("id").asLong();
    }
    private long createFlag(String token, long project, String key) throws Exception {
        return body(send("POST", token, flagsPath(project), Map.of("name", "Test flag", "key", key))
                .andExpect(status().isCreated())).get("id").asLong();
    }
    private ResultActions getRequest(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }
    private ResultActions send(String method, String token, String path, Object body) throws Exception {
        var request = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        request.header("Authorization", "Bearer " + token);
        if (body != null) { request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)); }
        return mvc.perform(request);
    }
    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private String email() { return "core-" + UUID.randomUUID() + "@example.test"; }
    private String projectPath(long project) { return "/api/v1/projects/" + project; }
    private String envPath(long project) { return projectPath(project) + "/environments"; }
    private String flagsPath(long project) { return projectPath(project) + "/flags"; }
    private String flagPath(long project, long flag) { return flagsPath(project) + "/" + flag; }
    private String configPath(long flag, long environment) { return "/api/v1/flags/" + flag + "/environments/" + environment; }
    private String auditPath(long project) { return projectPath(project) + "/audit"; }
}
