package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.AppointmentStatus;

public record StatusCountResponse(
        AppointmentStatus status,
        long count
) {
}
