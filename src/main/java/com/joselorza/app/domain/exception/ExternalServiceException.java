package com.joselorza.app.domain.exception;

/**
 * Excepción lanzada cuando falla la comunicación con un servicio externo
 * (pasarela de pago, Google Calendar). El GlobalExceptionHandler la traduce a HTTP 502.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
