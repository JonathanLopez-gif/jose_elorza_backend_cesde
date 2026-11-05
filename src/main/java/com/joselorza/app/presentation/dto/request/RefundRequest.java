package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Registro de reembolso manual. Si amount es null se calcula automáticamente
 * (monto pagado menos la penalización por cancelación tardía, si aplica).
 */
public record RefundRequest(

        @PositiveOrZero(message = "The refund amount can't be negative")
        BigDecimal amount,

        @Size(max = 500, message = "The reason can't exceed 500 characters")
        String reason

) {
}
