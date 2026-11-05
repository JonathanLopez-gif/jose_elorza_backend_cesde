package com.joselorza.app.domain.exception;

/**
 * Excepción lanzada cuando la firma/autenticidad de un webhook de pasarela no es válida.
 * El GlobalExceptionHandler la traduce a HTTP 401.
 */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("Invalid webhook signature");
    }
}
