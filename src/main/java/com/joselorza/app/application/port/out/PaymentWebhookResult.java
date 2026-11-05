package com.joselorza.app.application.port.out;

import com.joselorza.app.domain.model.enumerated.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado normalizado de un webhook de pasarela, ya con la firma validada.
 * paymentId corresponde a la referencia enviada al iniciar el pago (null si no es reconocible).
 */
public record PaymentWebhookResult(
        UUID paymentId,
        String gatewayTransactionId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String rawPayload
) {
}
