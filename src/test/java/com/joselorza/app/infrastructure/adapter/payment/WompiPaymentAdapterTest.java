package com.joselorza.app.infrastructure.adapter.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joselorza.app.application.port.out.PaymentInitiationRequest;
import com.joselorza.app.application.port.out.PaymentInitiationResult;
import com.joselorza.app.application.port.out.PaymentWebhookResult;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.InvalidWebhookSignatureException;
import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import com.joselorza.app.infrastructure.security.SignatureUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WompiPaymentAdapterTest {

    private static final String EVENTS_SECRET = "test_events_secret";
    private static final String INTEGRITY_SECRET = "test_integrity_secret";

    private WompiPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        WompiProperties properties = new WompiProperties();
        properties.setEnabled(true);
        properties.setPublicKey("pub_test_123");
        properties.setIntegritySecret(INTEGRITY_SECRET);
        properties.setEventsSecret(EVENTS_SECRET);
        properties.setCheckoutUrl("https://checkout.wompi.co/p/");
        properties.setRedirectUrl("https://joselorza.com/pagos/resultado");

        adapter = new WompiPaymentAdapter(properties, new ObjectMapper());
    }

    @Test
    void initiatePayment_buildsCheckoutUrlWithIntegritySignature() {
        UUID paymentId = UUID.randomUUID();

        PaymentInitiationResult result = adapter.initiatePayment(new PaymentInitiationRequest(
                paymentId, UUID.randomUUID(), new BigDecimal("119000.00"), Currency.COP,
                PaymentType.BOOKING, "ana@test.com", "Ana", "Reserva"));

        String expectedSignature = SignatureUtils.sha256Hex(paymentId + "11900000" + "COP" + INTEGRITY_SECRET);

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.checkoutUrl())
                .startsWith("https://checkout.wompi.co/p/?public-key=pub_test_123")
                .contains("amount-in-cents=11900000")
                .contains("reference=" + paymentId)
                .contains("signature:integrity=" + expectedSignature);
    }

    @Test
    void initiatePayment_inUsd_isRejected() {
        PaymentInitiationRequest request = new PaymentInitiationRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), Currency.USD,
                PaymentType.BOOKING, "ana@test.com", "Ana", "Reserva");

        assertThatThrownBy(() -> adapter.initiatePayment(request))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void processWebhook_withValidChecksum_returnsApprovedResult() {
        UUID paymentId = UUID.randomUUID();
        String payload = webhook(paymentId, "APPROVED", validChecksum("tx-1", "APPROVED"));

        PaymentWebhookResult result = adapter.processWebhook(payload, Map.of());

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.gatewayTransactionId()).isEqualTo("tx-1");
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.amount()).isEqualByComparingTo("119000.00");
        assertThat(result.currency()).isEqualTo("COP");
    }

    @Test
    void processWebhook_declined_mapsToFailed() {
        String payload = webhook(UUID.randomUUID(), "DECLINED", validChecksum("tx-1", "DECLINED"));

        assertThat(adapter.processWebhook(payload, Map.of()).status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void processWebhook_withTamperedStatus_isRejected() {
        // Firma calculada para DECLINED pero el cuerpo dice APPROVED
        String payload = webhook(UUID.randomUUID(), "APPROVED", validChecksum("tx-1", "DECLINED"));

        assertThatThrownBy(() -> adapter.processWebhook(payload, Map.of()))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    private String validChecksum(String transactionId, String status) {
        return SignatureUtils.sha256Hex(transactionId + status + "11900000" + "1700000000" + EVENTS_SECRET);
    }

    private String webhook(UUID reference, String status, String checksum) {
        return """
                {
                  "event": "transaction.updated",
                  "data": {
                    "transaction": {
                      "id": "tx-1",
                      "amount_in_cents": 11900000,
                      "reference": "%s",
                      "currency": "COP",
                      "status": "%s"
                    }
                  },
                  "environment": "test",
                  "signature": {
                    "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"],
                    "checksum": "%s"
                  },
                  "timestamp": 1700000000
                }
                """.formatted(reference, status, checksum);
    }
}
