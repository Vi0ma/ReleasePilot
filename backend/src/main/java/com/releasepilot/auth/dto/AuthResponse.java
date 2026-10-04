package com.releasepilot.auth.dto;

import com.releasepilot.user.dto.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    @Override public String toString() { return "AuthResponse[redacted]"; }
}
