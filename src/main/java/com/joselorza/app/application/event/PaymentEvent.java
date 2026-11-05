package com.joselorza.app.application.event;

import java.util.UUID;

/**
 * Evento de dominio publicado cuando un pago se completa o se reembolsa.
 */
public record PaymentEvent(UUID paymentId, PaymentEventType type) {
}
