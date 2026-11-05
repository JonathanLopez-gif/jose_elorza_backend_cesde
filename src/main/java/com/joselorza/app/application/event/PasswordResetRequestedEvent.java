package com.joselorza.app.application.event;

import java.util.UUID;

/**
 * Evento publicado al solicitar recuperación de contraseña.
 * Lleva el token en claro solo en memoria (en BD se guarda únicamente su hash).
 */
public record PasswordResetRequestedEvent(UUID userId, String rawToken) {
}
