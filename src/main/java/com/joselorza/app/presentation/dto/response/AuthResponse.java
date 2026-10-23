package com.joselorza.app.presentation.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
