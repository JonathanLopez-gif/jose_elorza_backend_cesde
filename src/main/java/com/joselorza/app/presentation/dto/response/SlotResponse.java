package com.joselorza.app.presentation.dto.response;

import java.time.Instant;

/** Horario de inicio reservable (UTC). available = false si ya está ocupado. */
public record SlotResponse(
        Instant startUtc,
        Instant endUtc,
        boolean available
) {}
