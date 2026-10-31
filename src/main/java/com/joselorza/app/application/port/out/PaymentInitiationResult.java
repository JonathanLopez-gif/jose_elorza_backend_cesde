package com.joselorza.app.application.port.out;

import com.joselorza.app.domain.model.enumerated.PaymentStatus;

/**
 * Resultado de iniciar un pago en una pasarela.
 * gatewayTransactionId y rawResponse pueden ser null para pagos manuales.
 */
public record PaymentInitiationResult(
        String gatewayTransactionId,
        PaymentStatus status,
        String rawResponse
) {
}