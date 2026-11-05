package com.joselorza.app.application.port.out;

import java.time.Instant;

/**
 * Datos de un evento a crear/actualizar en el calendario del administrador.
 */
public record CalendarEventData(
        String summary,
        String description,
        Instant start,
        Instant end,
        String location
) {
}
