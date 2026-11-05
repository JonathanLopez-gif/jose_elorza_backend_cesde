package com.joselorza.app.infrastructure.adapter.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.application.port.out.PaymentInitiationRequest;
import com.joselorza.app.application.port.out.PaymentInitiationResult;
import com.joselorza.app.application.port.out.PaymentWebhookResult;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.InvalidWebhookSignatureException;
import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.infrastructure.security.SignatureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de Wompi (Colombia, solo COP).
 * - initiatePayment: construye el enlace de Web Checkout con firma de integridad.
 *   La referencia enviada a Wompi es el ID interno del Payment.
 * - processWebhook: valida el checksum del evento "transaction.updated"
 *   (SHA256 de las propiedades indicadas + timestamp + secreto de eventos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WompiPaymentAdapter implements PaymentGatewayPort {

    private static final String TRANSACTION_UPDATED_EVENT = "transaction.updated";

    private final WompiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentGateway getGatewayType() {
        return PaymentGateway.WOMPI;
    }

    @Override
    public PaymentInitiationResult initiatePayment(PaymentInitiationRequest request) {
        ensureConfigured();

        if (request.currency() != Currency.COP) {
            throw new BusinessRuleException("Wompi only supports payments in COP");
        }

        String reference = request.paymentId().toString();
        long amountInCents = toCents(request.amount());
        String currency = request.currency().name();

        // Firma de integridad: SHA256("<referencia><monto_en_centavos><moneda><secreto_integridad>")
        String integritySignature = SignatureUtils.sha256Hex(
                reference + amountInCents + currency + properties.getIntegritySecret());

        StringBuilder url = new StringBuilder(properties.getCheckoutUrl())
                .append("?public-key=").append(encode(properties.getPublicKey()))
                .append("&currency=").append(currency)
                .append("&amount-in-cents=").append(amountInCents)
                .append("&reference=").append(encode(reference))
                .append("&signature:integrity=").append(integritySignature);

        if (StringUtils.hasText(properties.getRedirectUrl())) {
            // El frontend usa paymentId para consultar el estado al volver; Wompi añade &id=<transacción>
            url.append("&redirect-url=").append(encode(properties.getRedirectUrl() + "?paymentId=" + reference));
        }
        if (StringUtils.hasText(request.customerEmail())) {
            url.append("&customer-data:email=").append(encode(request.customerEmail()));
        }
        if (StringUtils.hasText(request.customerName())) {
            url.append("&customer-data:full-name=").append(encode(request.customerName()));
        }

        // El ID de transacción de Wompi solo se conoce cuando llega el webhook
        return new PaymentInitiationResult(null, PaymentStatus.PENDING, null, url.toString());
    }

    @Override
    public PaymentWebhookResult processWebhook(String payload, Map<String, String> headers) {
        ensureConfigured();

        JsonNode root = parse(payload);
        validateChecksum(root);

        String event = root.path("event").asText();
        if (!TRANSACTION_UPDATED_EVENT.equals(event)) {
            log.info("Wompi event ignored: {}", event);
            return new PaymentWebhookResult(null, null, PaymentStatus.PENDING, null, null, payload);
        }

        JsonNode transaction = root.path("data").path("transaction");

        return new PaymentWebhookResult(
                parseReference(transaction.path("reference").asText(null)),
                transaction.path("id").asText(null),
                mapStatus(transaction.path("status").asText()),
                BigDecimal.valueOf(transaction.path("amount_in_cents").asLong()).movePointLeft(2),
                transaction.path("currency").asText(null),
                payload
        );
    }

    /**
     * checksum = SHA256(valores de signature.properties concatenados + timestamp + events_secret)
     */
    private void validateChecksum(JsonNode root) {
        JsonNode signature = root.path("signature");
        String receivedChecksum = signature.path("checksum").asText(null);
        JsonNode propertiesNode = signature.path("properties");

        if (receivedChecksum == null || !propertiesNode.isArray() || !root.has("timestamp")) {
            throw new InvalidWebhookSignatureException();
        }

        JsonNode data = root.path("data");
        StringBuilder toSign = new StringBuilder();
        for (JsonNode property : propertiesNode) {
            JsonNode value = data;
            for (String part : property.asText().split("\\.")) {
                value = value.path(part);
            }
            toSign.append(value.asText());
        }
        toSign.append(root.path("timestamp").asText());
        toSign.append(properties.getEventsSecret());

        String expected = SignatureUtils.sha256Hex(toSign.toString());
        if (!SignatureUtils.constantTimeEquals(expected, receivedChecksum)) {
            throw new InvalidWebhookSignatureException();
        }
    }

    private PaymentStatus mapStatus(String wompiStatus) {
        return switch (wompiStatus) {
            case "APPROVED" -> PaymentStatus.COMPLETED;
            case "DECLINED", "VOIDED", "ERROR" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private UUID parseReference(String reference) {
        try {
            return reference == null ? null : UUID.fromString(reference);
        } catch (IllegalArgumentException e) {
            log.warn("Wompi webhook with unknown reference: {}", reference);
            return null;
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new InvalidWebhookSignatureException();
        }
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessRuleException("The Wompi gateway is not enabled");
        }
    }

    private static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
