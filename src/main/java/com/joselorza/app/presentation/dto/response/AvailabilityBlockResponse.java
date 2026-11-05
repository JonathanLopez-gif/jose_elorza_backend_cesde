package com.joselorza.app.presentation.dto.response;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Representa un rango horario disponible dentro de un día.
 * startTime/endTime están en la zona horaria solicitada;
 * startAt/endAt son los instantes UTC equivalentes (para reservar).
 */
public record AvailabilityBlockResponse(
        LocalTime startTime,
        LocalTime endTime,
        Instant startAt,
        Instant endAt
) {}
