package com.joselorza.app.presentation.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken, // También se envía en la cookie httpOnly para el frontend web
        long expiresIn,
        UserResponse user
) {
}
