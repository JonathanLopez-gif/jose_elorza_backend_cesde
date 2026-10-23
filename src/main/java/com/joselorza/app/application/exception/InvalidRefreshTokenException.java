package com.joselorza.app.application.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid, expired, or revoked refresh token");
    }
}
