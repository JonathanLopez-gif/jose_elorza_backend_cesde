package com.joselorza.app.application.port.out;

import com.joselorza.app.domain.model.enumerated.PaymentGateway;

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
     * Para pasarelas reales, aquí se haría la llamada HTTP externa.
     */
    PaymentInitiationResult initiatePayment(PaymentInitiationRequest request);
}