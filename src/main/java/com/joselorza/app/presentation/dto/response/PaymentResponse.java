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
 * checkoutUrl es el enlace al que el frontend debe redirigir para pagar (Wompi / Global66).
 * amount incluye IVA; baseAmount + taxAmount = amount.
 * refundDue es el reembolso sugerido cuando la cita fue cancelada (null si no aplica).
 */
public record PaymentResponse(
        UUID id,
        UUID appointmentId,
        UUID userId,
        String userName,
        String userEmail,
        String servicePlanName,
        PaymentGateway gateway,
        String gatewayTransactionId,
        BigDecimal amount,
        BigDecimal baseAmount,
        BigDecimal taxAmount,
        Currency currency,
        PaymentStatus status,
        PaymentType paymentType,
        String checkoutUrl,
        Instant paidAt,
        BigDecimal refundDue,
        BigDecimal refundedAmount,
        String refundReason,
        Instant refundedAt,
        Instant createdAt
) {
}
