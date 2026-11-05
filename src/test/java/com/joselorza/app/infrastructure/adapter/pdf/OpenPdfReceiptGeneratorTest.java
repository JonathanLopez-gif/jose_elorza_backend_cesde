package com.joselorza.app.infrastructure.adapter.pdf;

import com.joselorza.app.application.port.out.ReceiptData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OpenPdfReceiptGeneratorTest {

    @Test
    void generate_returnsValidPdfDocument() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        ReceiptData data = new ReceiptData("123", "tx-1", now, now, "José Fernando Elorza", "900123",
                "contacto@joselorza.com", "Ana", "ana@test.com", null, "Mentoría", "Reserva de servicio",
                now.plusDays(3), "WOMPI", "COP", new BigDecimal("100000.00"), new BigDecimal("0.19"),
                new BigDecimal("19000.00"), new BigDecimal("119000.00"), "COMPLETED", null);

        byte[] pdf = new OpenPdfReceiptGenerator().generate(data);

        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
