package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(

        @Size(max = 500, message = "The reason can't exceed 500 characters")
        String reason

) {
}
