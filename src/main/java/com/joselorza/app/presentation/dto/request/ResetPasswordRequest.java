package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "The token is required")
        String token,

        @NotBlank
        @Size(min = 8, message = "The password must be at least 8 characters long")
        String newPassword
) {
}
