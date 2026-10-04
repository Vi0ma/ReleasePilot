package com.releasepilot.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasepilot.security.JwtService;
import com.releasepilot.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {
    private static final String PASSWORD = "StrongPassword123!";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JwtService jwt;

    @Test
    void registerNormalizesEmailAndStoresOnlyBcrypt() throws Exception {
        String email = email();
        var result = register("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ")
                .andExpect(status().isCreated()).andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist()).andReturn();
        var user = users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$12$");
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD, user.getPasswordHash());
    }

    @Test
    void duplicateNormalizedEmailReturnsConflict() throws Exception {
        String email = email();
        register(email).andExpect(status().isCreated());
        register(" " + email.toUpperCase(java.util.Locale.ROOT) + " ")
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void loginReturnsJwtAndProtectedMeIsAccessibleWithoutSession() throws Exception {
        String email = email();
        register(email).andExpect(status().isCreated());
        var result = login(email.toUpperCase(java.util.Locale.ROOT), PASSWORD)
                .andExpect(status().isOk()).andExpect(jsonPath("tokenType").value("Bearer"))
                .andExpect(jsonPath("expiresIn").value(900))
                .andExpect(jsonPath("user.passwordHash").doesNotExist()).andReturn();
        String body = result.getResponse().getContentAsString();
        String token = json.readTree(body).get("accessToken").asText();
        var user = users.findByEmail(email).orElseThrow();
        assertThat(jwt.validate(token).getSubject()).isEqualTo(user.getId().toString());
        assertThat(body).doesNotContain(PASSWORD, user.getPasswordHash());
        var me = mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("passwordHash").doesNotExist()).andReturn();
        assertThat(me.getRequest().getSession(false)).isNull();
        assertThat(me.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(me.getResponse().getContentAsString()).doesNotContain(PASSWORD, user.getPasswordHash());
    }

    @Test
    void wrongPasswordAndUnknownUserHaveSameUnauthorizedResponse() throws Exception {
        String email = email();
        register(email).andExpect(status().isCreated());
        login(email, "WrongPassword123!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("message").value("Invalid email or password"))
                .andExpect(jsonPath("trace").doesNotExist());
        login(email(), PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("message").value("Invalid email or password"));
    }

    @Test
    void protectedRoutesRequireValidBearerToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/future-route")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("trace").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwt.generate(Long.MAX_VALUE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputAndOversizedUtf8PasswordsAreRejectedWithoutLeakingValues() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "", "email", "invalid", "password", "short"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("fieldErrors.email").exists())
                .andExpect(jsonPath("fieldErrors.password").exists()).andExpect(jsonPath("trace").doesNotExist());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Test", "email", email(), "password", "é".repeat(40)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("fieldErrors.password").exists());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("status").value("UP"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Test User", "email", email, "password", PASSWORD))));
    }
    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }
    private String email() { return "auth-" + UUID.randomUUID() + "@example.test"; }
}
