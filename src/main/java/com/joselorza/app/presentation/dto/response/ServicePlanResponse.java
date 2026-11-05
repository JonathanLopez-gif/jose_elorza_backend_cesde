package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.SessionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida para exponer un ServicePlan en las respuestas de la API.
 */
public record ServicePlanResponse(
        UUID id,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal priceCop,
        BigDecimal priceUsd,
        Modality modality,
        SessionType sessionType,
        Integer maxCapacity,
        BigDecimal taxRate,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}