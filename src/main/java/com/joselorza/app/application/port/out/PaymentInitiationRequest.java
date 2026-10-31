package com.joselorza.app.application.port.out;

import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos necesarios para iniciar un pago en cualquier pasarela.
 */
public record PaymentInitiationRequest(
        UUID appointmentId,
        BigDecimal amount,
        Currency currency,
        PaymentType paymentType
) {
}