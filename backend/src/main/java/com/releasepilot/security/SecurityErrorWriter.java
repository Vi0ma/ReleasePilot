package com.releasepilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasepilot.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper json;
    public SecurityErrorWriter(ObjectMapper json) { this.json = json; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == 401) { response.setHeader("WWW-Authenticate", "Bearer"); }
        json.writeValue(response.getOutputStream(), ApiError.of(status,
                status == 401 ? "UNAUTHORIZED" : "FORBIDDEN",
                status == 401 ? "Authentication required" : "Access denied", request.getRequestURI()));
    }
}
