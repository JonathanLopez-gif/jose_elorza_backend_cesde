package com.joselorza.app.presentation.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityExceptionResponse(
        UUID id,
        LocalDate date,
        Boolean isAvailable,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {}