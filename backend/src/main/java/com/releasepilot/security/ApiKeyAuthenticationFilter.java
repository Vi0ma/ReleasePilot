package com.releasepilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.releasepilot.apikey.service.ApiKeyAuthenticationService;
import com.releasepilot.shared.error.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final ApiKeyAuthenticationService keys;
    private final ObjectMapper json;
    public ApiKeyAuthenticationFilter(ApiKeyAuthenticationService keys, ObjectMapper json) {
        this.keys = keys;
        this.json = json;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("X-API-Key"));
        try {
            var principal = headers.size() == 1 ? keys.authenticate(headers.getFirst()) : java.util.Optional.<ApiKeyPrincipal>empty();
            if (principal.isEmpty()) {
                response.setHeader("WWW-Authenticate", "ApiKey");
                error(request, response, 401, "UNAUTHORIZED", "Valid environment API key required");
                return;
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal.get(), null,
                    List.of(new SimpleGrantedAuthority("EVALUATE"))));
            SecurityContextHolder.setContext(context);
        } catch (DataAccessException ex) {
            error(request, response, 503, "SERVICE_UNAVAILABLE", "Evaluation authentication unavailable");
            return;
        }
        chain.doFilter(request, response);
    }
    private void error(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiError.of(status, code, message, request.getRequestURI()));
    }
}
