package com.joselorza.app.application.event;

import java.util.UUID;

/**
 * Evento de dominio publicado cuando cambia el estado relevante de una cita.
 * Se procesa tras el commit (correo + Google Calendar) para no afectar la transacción.
 */
public record AppointmentEvent(UUID appointmentId, AppointmentEventType type) {
}
