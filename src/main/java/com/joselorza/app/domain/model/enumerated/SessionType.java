package com.joselorza.app.domain.model.enumerated;

/**
 * Tipo de sesión: individual o grupal.
 */
public enum SessionType {
    INDIVIDUAL, // Una persona por cita (maxCapacity debe ser null)
    GROUP       // Varias personas por cita (maxCapacity entre 2 y 10)
}