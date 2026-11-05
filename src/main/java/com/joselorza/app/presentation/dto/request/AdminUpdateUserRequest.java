package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.presentation.validation.ValidTimezone;
import jakarta.validation.constraints.NotBlank;

public record AdminUpdateUserRequest(
        @NotBlank
        String name,

        @NotBlank
        @ValidTimezone
        String timezone,

        String phone,

        boolean notifyEmail,

        Role role // opcional; si es null se conserva el actual
) {
}
