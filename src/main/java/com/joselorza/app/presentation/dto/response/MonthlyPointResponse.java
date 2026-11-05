package com.joselorza.app.presentation.dto.response;

import java.math.BigDecimal;

/**
 * Punto de la serie mensual del dashboard: ingresos netos por moneda y citas atendidas/agendadas.
 */
public record MonthlyPointResponse(
        String month, // AAAA-MM
        BigDecimal incomeCop,
        BigDecimal incomeUsd,
        long appointments
) {
}
