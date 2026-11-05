package com.joselorza.app.infrastructure.adapter.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Credenciales y URLs de Wompi (sandbox o producción según las llaves configuradas).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payments.wompi")
public class WompiProperties {
    private boolean enabled;
    private String publicKey;
    private String integritySecret;
    private String eventsSecret;
    private String checkoutUrl;
    private String redirectUrl;

    public boolean isConfigured() {
        return enabled
                && StringUtils.hasText(publicKey)
                && StringUtils.hasText(integritySecret)
                && StringUtils.hasText(eventsSecret);
    }
}
