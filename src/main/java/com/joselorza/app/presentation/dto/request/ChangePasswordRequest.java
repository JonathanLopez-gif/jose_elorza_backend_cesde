package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "The current password is required")
        String currentPassword,

        @NotBlank
        @Size(min = 8, message = "The password must be at least 8 characters long")
        String newPassword
) {
}
