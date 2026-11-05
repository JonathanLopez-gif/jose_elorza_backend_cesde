package com.joselorza.app.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long accessExpirationMinutes;
    private long refreshExpirationDays;
    private long passwordResetExpirationMinutes = 30;

    // Cookie httpOnly donde viaja el refresh token hacia el frontend web
    private String refreshCookieName = "refresh_token";
    private boolean refreshCookieSecure = false; // true en producción (HTTPS)
    private String refreshCookieSameSite = "Lax";
}
