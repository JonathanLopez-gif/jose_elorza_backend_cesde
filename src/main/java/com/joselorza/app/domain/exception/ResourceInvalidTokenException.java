package com.joselorza.app.domain.exception;

public class ResourceInvalidTokenException extends RuntimeException {
    public ResourceInvalidTokenException() {
        super("Invalid token");
    }
}
