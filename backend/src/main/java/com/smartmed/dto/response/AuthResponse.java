package com.smartmed.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static AuthResponse of(String accessToken, long expiresInMs, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs, user);
    }
}
