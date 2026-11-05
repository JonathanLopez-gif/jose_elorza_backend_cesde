package com.joselorza.app.application.port.out;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;

import java.util.Map;

/**
 * Puerto de salida para procesar pagos.
 * Cada pasarela (Manual, Wompi, Global66) implementa este contrato.
 */
public interface PaymentGatewayPort {

    /**
     * Identifica qué tipo de pasarela implementa este adaptador.
     */
    PaymentGateway getGatewayType();

    /**
     * Inicia el procesamiento de un pago.
     * Para MANUAL, simplemente retorna estado PENDING (la confirmación
     * la hace un administrador vía endpoint).
     * Para pasarelas reales, genera el enlace/orden de pago externo.
     */
    PaymentInitiationResult initiatePayment(PaymentInitiationRequest request);

    /**
     * Valida la autenticidad (firma) de un webhook y lo traduce a un resultado
     * independiente de la pasarela. Debe lanzar InvalidWebhookSignatureException
     * si la firma no es válida.
     */
    default PaymentWebhookResult processWebhook(String payload, Map<String, String> headers) {
        throw new BusinessRuleException("The gateway " + getGatewayType() + " doesn't support webhooks");
    }
}
