package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.Currency;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Request para crear una cita/reserva.
 * scheduledAt es opcional: para servicios RECORDED se ignora y se asigna
 * automáticamente la fecha de compra; para LIVE/ONLINE/IN_PERSON es obligatorio
 * (validado en el servicio, no aquí, porque depende de la modalidad).
 */
public record AppointmentRequest(

        @NotNull(message = "The service is required")
        UUID servicePlanId,

        @Future(message = "The scheduled date must be in the future")
        Instant scheduledAt,

        @NotNull(message = "The currency is required")
        Currency currency

) {
}