package com.joselorza.app.presentation.dto.response;

import java.time.Instant;

/**
 * Estructura estándar de respuesta de error para toda la API.
 * code es opcional y identifica casos de negocio concretos (ej. SLOT_UNAVAILABLE).
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String code
) {
}
