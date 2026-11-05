package com.joselorza.app.infrastructure.adapter.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simulador de pasarelas SOLO para desarrollo y demos.
 * Si está habilitado, los pagos WOMPI / GLOBAL66 no salen a la pasarela real:
 * el checkout apunta a la página simuladora del frontend y el resultado se
 * informa vía POST /api/payments/{id}/sandbox-result. Nunca habilitar en producción.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payments.sandbox")
public class PaymentSandboxProperties {
    private boolean enabled = false;
    private String checkoutUrl;
}
