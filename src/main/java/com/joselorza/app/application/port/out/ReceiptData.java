package com.joselorza.app.application.port.out;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Información que se imprime en el comprobante de pago.
 * Los montos ya vienen calculados: base + IVA = total.
 */
public record ReceiptData(
        String receiptNumber,
        String transactionNumber,
        ZonedDateTime issuedAt,
        ZonedDateTime paidAt,
        String issuerName,
        String issuerId,
        String issuerEmail,
        String customerName,
        String customerEmail,
        String customerPhone,
        String serviceName,
        String concept,
        ZonedDateTime appointmentAt,
        String gateway,
        String currency,
        BigDecimal baseAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String status,
        BigDecimal refundedAmount
) {
}
