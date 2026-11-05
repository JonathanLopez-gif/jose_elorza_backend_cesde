package com.joselorza.app.infrastructure.adapter.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.application.port.out.PaymentInitiationRequest;
import com.joselorza.app.application.port.out.PaymentInitiationResult;
import com.joselorza.app.application.port.out.PaymentWebhookResult;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ExternalServiceException;
import com.joselorza.app.domain.exception.InvalidWebhookSignatureException;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.infrastructure.security.SignatureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de Global66 (pagos internacionales, principalmente USD).
 * - initiatePayment: crea una orden de pago vía API REST y retorna el enlace de pago.
 * - processWebhook: valida la firma HMAC-SHA256 del cuerpo crudo con el secreto del webhook.
 *
 * NOTA: Global66 entrega la especificación de su API de cobros a cada comercio.
 * El path de la orden, el header de firma y los nombres de campos están
 * centralizados aquí (y parametrizados en Global66Properties) para ajustarlos
 * al contrato definitivo sin tocar la lógica de negocio.
 */
@Slf4j
@Component
public class Global66PaymentAdapter implements PaymentGatewayPort {

    private final Global66Properties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public Global66PaymentAdapter(Global66Properties properties, ObjectMapper objectMapper,
                                  RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public PaymentGateway getGatewayType() {
        return PaymentGateway.GLOBAL66;
    }

    @Override
    public PaymentInitiationResult initiatePayment(PaymentInitiationRequest request) {
        ensureConfigured();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference", request.paymentId().toString());
        body.put("amount", request.amount());
        body.put("currency", request.currency().name());
        body.put("description", request.description());
        body.put("customer", Map.of(
                "email", request.customerEmail(),
                "name", request.customerName()));
        body.put("redirectUrl", properties.getRedirectUrl() + "?paymentId=" + request.paymentId());
        body.put("notificationUrl", properties.getNotificationUrl());

        try {
            String response = restClient.post()
                    .uri(properties.getApiUrl() + properties.getOrderPath())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(response);
            String orderId = firstText(json, "id", "orderId");
            String paymentUrl = firstText(json, "paymentUrl", "checkoutUrl", "url");

            if (paymentUrl == null) {
                throw new ExternalServiceException("Global66 did not return a payment link");
            }

            return new PaymentInitiationResult(orderId, PaymentStatus.PENDING, response, paymentUrl);
        } catch (RestClientException | IOException e) {
            log.error("Error creating Global66 payment order for payment {}", request.paymentId(), e);
            throw new ExternalServiceException("Could not create the payment order in Global66", e);
        }
    }

    @Override
    public PaymentWebhookResult processWebhook(String payload, Map<String, String> headers) {
        ensureConfigured();

        String receivedSignature = headers.get(properties.getSignatureHeader());
        String expectedSignature = SignatureUtils.hmacSha256Hex(payload, properties.getWebhookSecret());

        if (!SignatureUtils.constantTimeEquals(expectedSignature, receivedSignature)) {
            throw new InvalidWebhookSignatureException();
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new InvalidWebhookSignatureException();
        }

        // Algunos eventos envuelven la orden en "data"
        JsonNode order = json.has("data") ? json.path("data") : json;

        String amount = firstText(order, "amount");

        return new PaymentWebhookResult(
                parseReference(firstText(order, "reference", "externalReference")),
                firstText(order, "id", "orderId", "transactionId"),
                mapStatus(firstText(order, "status")),
                amount == null ? null : new BigDecimal(amount),
                firstText(order, "currency"),
                payload
        );
    }

    private PaymentStatus mapStatus(String status) {
        if (status == null) {
            return PaymentStatus.PENDING;
        }
        return switch (status.toUpperCase()) {
            case "PAID", "APPROVED", "COMPLETED", "SUCCESS" -> PaymentStatus.COMPLETED;
            case "REJECTED", "FAILED", "DECLINED", "EXPIRED", "CANCELLED", "CANCELED" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private UUID parseReference(String reference) {
        try {
            return reference == null ? null : UUID.fromString(reference);
        } catch (IllegalArgumentException e) {
            log.warn("Global66 webhook with unknown reference: {}", reference);
            return null;
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessRuleException("The Global66 gateway is not enabled");
        }
    }
}
