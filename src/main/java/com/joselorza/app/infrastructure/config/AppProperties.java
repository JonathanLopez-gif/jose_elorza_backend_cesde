package com.joselorza.app.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propiedades generales de la aplicación (prefijo "app").
 * Las secciones específicas (jwt, scheduling, payments, etc.) tienen su propia clase.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // URL base del frontend, usada para construir enlaces en los correos
    private String frontendUrl;

    // Orígenes autorizados por CORS (lista separada por comas en la variable de entorno)
    private List<String> corsAllowedOrigins = List.of("http://localhost:5173");

    private Mail mail = new Mail();

    private Receipt receipt = new Receipt();

    @Getter
    @Setter
    public static class Mail {
        private String from;
        private String fromName;
    }

    // Datos del emisor que se imprimen en el comprobante PDF
    @Getter
    @Setter
    public static class Receipt {
        private String issuerName;
        private String issuerId;
        private String issuerEmail;
    }
}
