package com.joselorza.app.application.port.out;

import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos necesarios para iniciar un pago en cualquier pasarela.
 * paymentId se usa como referencia única ante la pasarela, para poder
 * conciliar el webhook con el Payment interno.
 */
public record PaymentInitiationRequest(
        UUID paymentId,
        UUID appointmentId,
        BigDecimal amount,
        Currency currency,
        PaymentType paymentType,
        String customerEmail,
        String customerName,
        String description
) {
}
