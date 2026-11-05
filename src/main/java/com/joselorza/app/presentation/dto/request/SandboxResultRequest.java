package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.application.service.PaymentService.SandboxResult;
import jakarta.validation.constraints.NotNull;

/**
 * Resultado que informa la página simuladora de pasarela (solo desarrollo).
 */
public record SandboxResultRequest(

        @NotNull(message = "The result is required")
        SandboxResult result

) {
}
