package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representa un pago (o intento de pago) asociado a una cita.
 */
public record PaymentResponse(
        UUID id,
        UUID appointmentId,
        PaymentGateway gateway,
        String gatewayTransactionId,
        BigDecimal amount,
        Currency currency,
        PaymentStatus status,
        PaymentType paymentType,
        Instant createdAt
) {
}