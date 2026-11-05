package com.joselorza.app.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Construye la cookie httpOnly del refresh token. El token nunca queda accesible
 * desde JavaScript, y solo se envía a los endpoints de /api/auth.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    private static final String COOKIE_PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public String cookieName() {
        return jwtProperties.getRefreshCookieName();
    }

    public ResponseCookie create(String rawRefreshToken) {
        return base(rawRefreshToken)
                .maxAge(Duration.ofDays(jwtProperties.getRefreshExpirationDays()))
                .build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .path(COOKIE_PATH);
    }
}
