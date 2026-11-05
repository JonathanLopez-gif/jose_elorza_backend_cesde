package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.presentation.validation.ValidTimezone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creación de usuarios desde el panel administrativo (permite asignar rol).
 */
public record AdminCreateUserRequest(
        @NotBlank
        String name,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8, message = "The password must be at least 8 characters long")
        String password,

        Role role, // opcional; si es null se asigna USER

        @NotBlank
        @ValidTimezone
        String timezone,

        String phone
) {
}
