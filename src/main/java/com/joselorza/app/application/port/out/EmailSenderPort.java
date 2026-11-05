package com.joselorza.app.application.port.out;

/**
 * Puerto de salida para el envío de correos electrónicos (SMTP Gmail en producción).
 */
public interface EmailSenderPort {

    /**
     * Envía un correo HTML. Lanza una excepción si el envío falla,
     * para que el llamador registre el estado FAILED en el log de notificaciones.
     */
    void send(String to, String subject, String htmlBody);
}
