package com.joselorza.app.domain.exception;

/**
 * Excepción de dominio lanzada cuando un recurso solicitado no existe.
 * El GlobalExceptionHandler la traduce a HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("Resource not found");
    }
}