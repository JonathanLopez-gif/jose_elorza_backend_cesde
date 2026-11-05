package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record AvailabilityRuleRequest(

        @NotNull(message = "The day of the week is required")
        @Min(value = 1, message = "The day of the week must be between 1 and 7")
        @Max(value = 7, message = "The day of the week must be between 1 and 7")
        Integer dayOfWeek,

        @NotNull(message = "The start time is required")
        LocalTime startTime,

        @NotNull(message = "The end time is required")
        LocalTime endTime,

        Boolean isAvailable // opcional; si es null, el servicio asigna true

) {}