package com.joselorza.app.presentation.dto.response;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityRuleResponse(
        UUID id,
        Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Boolean isAvailable,
        Instant createdAt,
        Instant updatedAt
) {}
