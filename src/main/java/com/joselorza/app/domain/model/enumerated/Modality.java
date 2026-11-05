package com.joselorza.app.domain.model.enumerated;

/**
 * Modalidad de un plan de servicio.
 */
public enum Modality {
    LIVE,       // Sesión en vivo (virtual)
    RECORDED,   // Contenido grabado, compra directa sin cita
    ONLINE,     // Online pero no necesariamente en vivo (ej: webinar programado)
    IN_PERSON   // Presencial
}