package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Nuevo orden de los canales: lista completa de ids, del primero al último.
 */
public record SocialLinkOrderRequest(

        @NotEmpty(message = "The list of ids is required")
        List<UUID> ids

) {
}
