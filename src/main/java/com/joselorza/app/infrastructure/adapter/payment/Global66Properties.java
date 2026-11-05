package com.joselorza.app.infrastructure.adapter.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Credenciales y URLs de Global66.
 * El path de creación de órdenes y el header de firma son configurables
 * para ajustarse al contrato entregado por Global66 al comercio.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payments.global66")
public class Global66Properties {
    private boolean enabled;
    private String apiUrl;
    private String apiKey;
    private String webhookSecret;
    private String orderPath;
    private String signatureHeader;
    private String redirectUrl;
    private String notificationUrl;

    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(apiUrl)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(webhookSecret);
    }
}
