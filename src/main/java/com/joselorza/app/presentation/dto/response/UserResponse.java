package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String timezone,
        String phone,
        boolean notifyEmail,
        boolean active,
        Instant createdAt
) {
}
