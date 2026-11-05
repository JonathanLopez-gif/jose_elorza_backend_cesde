package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request de reagendamiento. gateway solo se usa si aplica penalización
 * (< 7 días); si es null se usa la misma pasarela del pago original.
 */
public record RescheduleRequest(

        @NotNull(message = "The new date and time are required")
        @Future(message = "The scheduled date must be in the future")
        Instant scheduledAt,

        PaymentGateway gateway

) {
}
