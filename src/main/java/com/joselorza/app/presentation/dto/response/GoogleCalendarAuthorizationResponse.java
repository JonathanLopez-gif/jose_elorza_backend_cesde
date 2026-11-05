package com.joselorza.app.presentation.dto.response;

/**
 * URL de consentimiento de Google a la que el panel debe redirigir al administrador.
 */
public record GoogleCalendarAuthorizationResponse(
        String authorizationUrl
) {
}
