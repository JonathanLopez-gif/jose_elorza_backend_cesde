package com.joselorza.app.presentation.exception;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.presentation.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones de la API.
 * Traduce excepciones de dominio y errores de validación de Bean Validation
 * en respuestas HTTP consistentes (ErrorResponse).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Captura cuando falta un parámetro requerido en la query string,
     * por ejemplo GET /api/availability sin ?date=...
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Missing required parameter: " + ex.getParameterName();
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Captura cuando un parámetro no puede convertirse al tipo esperado,
     * por ejemplo ?date=fecha-invalida en lugar de formato ISO (YYYY-MM-DD).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format(
                "The parameter '%s' has an invalid format. Received value: '%s'",
                ex.getName(), ex.getValue()
        );
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}