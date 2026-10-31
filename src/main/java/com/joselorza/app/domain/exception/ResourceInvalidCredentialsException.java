package com.joselorza.app.domain.exception;

public class ResourceInvalidCredentialsException extends RuntimeException {
    public ResourceInvalidCredentialsException() {
        super("Invalid credentials");
    }
}
