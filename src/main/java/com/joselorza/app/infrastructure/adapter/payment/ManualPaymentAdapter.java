package com.joselorza.app.infrastructure.adapter.payment;

import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.application.port.out.PaymentInitiationRequest;
import com.joselorza.app.application.port.out.PaymentInitiationResult;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Adaptador de pago manual.
 * No procesa nada automáticamente: el pago queda en estado PENDING
 * hasta que un administrador lo confirme manualmente mediante
 * PATCH /api/admin/payments/{id}/confirm.
 */
@Component
public class ManualPaymentAdapter implements PaymentGatewayPort {

    @Override
    public PaymentGateway getGatewayType() {
        return PaymentGateway.MANUAL;
    }

    @Override
    public PaymentInitiationResult initiatePayment(PaymentInitiationRequest request) {
        // No hay integración externa: el pago queda pendiente de confirmación manual.
        return new PaymentInitiationResult(null, PaymentStatus.PENDING, null);
    }
}