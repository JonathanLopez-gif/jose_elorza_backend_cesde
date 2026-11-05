package com.joselorza.app.presentation.dto.response;

import java.time.LocalDate;

/** Resumen de un día para el calendario: horarios totales y cuántos siguen libres. */
public record DayAvailabilityResponse(
        LocalDate date,
        int total,
        int available
) {}
