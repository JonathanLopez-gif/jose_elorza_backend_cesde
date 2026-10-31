package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.SessionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO de entrada para crear/actualizar un ServicePlan.
 * La validación condicional de maxCapacity (según sessionType)
 * se realiza en la capa de servicio, no aquí.
 */
public record ServicePlanRequest(

        @NotBlank(message = "The name is required")
        @Size(max = 150, message = "The name can't exceed 150 characters")
        String name,

        @Size(max = 1000, message = "The description can't exceed 1000 characters")
        String description,

        @NotNull(message = "The duration is required")
        @Positive(message = "The duration must be greater than 0")
        Integer durationMinutes,

        @NotNull(message = "The price in COP is required")
        @PositiveOrZero(message = "The price in COP can't be negative")
        BigDecimal priceCop,

        @NotNull(message = "The price in USD is required")
        @PositiveOrZero(message = "The price in USD can't be negative")
        BigDecimal priceUsd,

        @NotNull(message = "The modality is required")
        Modality modality,

        @NotNull(message = "The session type is required")
        SessionType sessionType,

        // Nullable: se valida en el Service según sessionType (null para INDIVIDUAL, 2-10 para GROUP)
        @Min(value = 2, message = "The minimum capacity is 2")
        @Max(value = 10, message = "The maximum capacity is 10")
        Integer maxCapacity,

        // Opcional: si es null, el Service aplica el default 0.19
        @DecimalMin(value = "0.0", message = "The tax rate can't be negative")
        @DecimalMax(value = "1.0", message = "The tax rate can't exceed 1.0")
        BigDecimal taxRate

) {}