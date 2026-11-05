package com.joselorza.app.domain.exception;

/**
 * Excepción de dominio lanzada cuando se viola una regla de negocio
 * (ej. validaciones condicionales que no pueden expresarse con Bean Validation).
 * El GlobalExceptionHandler la traduce a HTTP 400.
 * El código opcional permite al frontend reaccionar a casos concretos
 * (ej. SLOT_UNAVAILABLE para refrescar los horarios).
 */
public class BusinessRuleException extends RuntimeException {

    public static final String SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";

    private final String code;

    public BusinessRuleException(String message) {
        this(null, message);
    }

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
