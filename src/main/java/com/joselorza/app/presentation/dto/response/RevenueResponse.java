package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.Currency;

import java.math.BigDecimal;

/**
 * Ingresos del mes por moneda: bruto cobrado, reembolsado y neto.
 */
public record RevenueResponse(
        Currency currency,
        BigDecimal gross,
        BigDecimal refunded,
        BigDecimal net
) {
}
