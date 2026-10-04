package com.releasepilot.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "releasepilot.frontend.origin=http://127.0.0.1:5173")
@AutoConfigureMockMvc
class DashboardCorsIntegrationTest {
    @Autowired private MockMvc mvc;
    @Test void dashboardPreflightIsAllowedWithoutCredentials() throws Exception {
        mvc.perform(options("/api/v1/projects").header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
    @Test void anUntrustedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void corsDoesNotGrantDashboardOrEvaluationAccess() throws Exception {
        mvc.perform(get("/api/v1/projects").header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
        mvc.perform(post("/api/v1/evaluate").header("Origin", "http://127.0.0.1:5173")
                .contentType("application/json").content("{\"flagKey\":\"test\",\"userKey\":\"u\"}"))
                .andExpect(status().isUnauthorized());
    }
}
