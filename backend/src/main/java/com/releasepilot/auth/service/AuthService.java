package com.releasepilot.auth.service;

import com.releasepilot.auth.dto.AuthResponse;
import com.releasepilot.auth.dto.LoginRequest;
import com.releasepilot.auth.dto.RegisterRequest;
import com.releasepilot.security.JwtService;
import com.releasepilot.shared.error.ApiException;
import com.releasepilot.user.dto.UserResponse;
import com.releasepilot.user.entity.User;
import com.releasepilot.user.entity.UserRole;
import com.releasepilot.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email already registered");
        }
        User user = new User(request.name(), request.email(), passwords.encode(request.password()), UserRole.USER);
        // Flush here: the DB constraint also handles concurrent registrations.
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var user = users.findByEmail(request.email());
        boolean valid = passwords.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (!valid || user.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        User account = user.orElseThrow();
        return new AuthResponse(jwt.generate(account.getId()), "Bearer", jwt.expirationSeconds(), UserResponse.from(account));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long id) {
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }
}
