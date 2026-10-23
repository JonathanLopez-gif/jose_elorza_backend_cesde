package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.presentation.validation.ValidTimezone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

        String phone
) {
}
