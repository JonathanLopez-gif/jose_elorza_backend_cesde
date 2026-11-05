package com.joselorza.app.infrastructure.adapter.calendar;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Configuración OAuth2 de Google Calendar (token único del administrador).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.google-calendar")
public class GoogleCalendarProperties {
    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String calendarId = "primary";
    private String encryptionKey;
    private String postAuthorizationRedirect;

    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(redirectUri)
                && StringUtils.hasText(encryptionKey);
    }
}
