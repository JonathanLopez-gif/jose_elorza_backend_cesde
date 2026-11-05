package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import jakarta.validation.constraints.NotNull;

/**
 * Reintento de pago de la obligación pendiente de una cita.
 * currency solo aplica a pagos de reserva (la penalidad se paga en la moneda original).
 */
public record PaymentRetryRequest(

        @NotNull(message = "The payment gateway is required")
        PaymentGateway gateway,

        Currency currency

) {
}
