package com.releasepilot.security;

import com.releasepilot.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final SecurityErrorWriter errors;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users, SecurityErrorWriter errors) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7) {
                    errors.write(request, response, 401);
                    return;
                }
                Long id = Long.valueOf(jwt.validate(header.substring(7)).getSubject());
                var user = users.findById(id);
                if (user.isEmpty()) { errors.write(request, response, 401); return; }
                var authentication = new UsernamePasswordAuthenticationToken(new UserPrincipal(id), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.orElseThrow().getRole().name())));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                errors.write(request, response, 401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
