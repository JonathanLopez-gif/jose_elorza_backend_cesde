package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.Modality;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representa el estado completo de una cita, incluyendo
 * el último pago asociado (si existe) para que el usuario
 * sepa cuánto y hasta cuándo debe pagar.
 */
public record AppointmentResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        UUID servicePlanId,
        String servicePlanName,
        Modality modality,
        BigDecimal servicePriceCop, // Precio base del servicio (sin IVA)
        BigDecimal servicePriceUsd,
        BigDecimal taxRate,
        Instant scheduledAt,
        Integer durationMinutes,
        String userTimezone,
        AppointmentStatus status,
        String meetingLink,
        UUID groupKey,
        Boolean penaltyApplied,
        BigDecimal penaltyAmount,
        Instant paymentDueAt,
        Instant pendingRescheduledAt,
        String cancellationReason,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        PaymentResponse latestPayment
) {
}
