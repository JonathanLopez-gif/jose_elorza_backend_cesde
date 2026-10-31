package com.joselorza.app.domain.exception;

/**
 * Excepción de dominio lanzada cuando se viola una regla de negocio
 * (ej. validaciones condicionales que no pueden expresarse con Bean Validation).
 * El GlobalExceptionHandler la traduce a HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}