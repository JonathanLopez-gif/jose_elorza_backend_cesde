package com.joselorza.app.presentation.dto.response;

import java.time.LocalTime;

/** Representa un rango horario disponible dentro de un día. */
public record AvailabilityBlockResponse(
        LocalTime startTime,
        LocalTime endTime
) {}