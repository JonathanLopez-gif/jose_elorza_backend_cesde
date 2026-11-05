package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.presentation.validation.ValidTimezone;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        String name,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8, message = "The password must be at least 8 characters long")
        String password,

        @NotBlank
        @ValidTimezone
        String timezone,

        String phone,

        // Aceptación obligatoria de términos de uso y política de privacidad (Ley 1581 / GDPR)
        @NotNull(message = "You must accept the terms of use and the privacy policy")
        @AssertTrue(message = "You must accept the terms of use and the privacy policy")
        Boolean acceptTerms
) {
}
