package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.presentation.validation.ValidTimezone;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank
        String name,

        @NotBlank
        @ValidTimezone
        String timezone,

        String phone,

        boolean notifyEmail
) {
}
