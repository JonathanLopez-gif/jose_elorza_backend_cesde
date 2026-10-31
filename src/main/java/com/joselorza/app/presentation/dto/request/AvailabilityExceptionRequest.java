package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AvailabilityExceptionRequest(

        @NotNull(message = "The date is required")
        @FutureOrPresent(message = "The date can't be in the past")
        LocalDate date,

        @Size(max = 255, message = "The reason can't exceed 255 characters")
        String reason

        // isAvailable NO se expone en el request: en esta fase el servicio
        // siempre asigna false. Se añadirá cuando se soporte "abrir" disponibilidad.

) {}