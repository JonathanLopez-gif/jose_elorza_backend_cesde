package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AppointmentService;
import com.joselorza.app.infrastructure.security.UserPrincipal;
import com.joselorza.app.presentation.dto.request.AppointmentRequest;
import com.joselorza.app.presentation.dto.request.CancelAppointmentRequest;
import com.joselorza.app.presentation.dto.request.PaymentRetryRequest;
import com.joselorza.app.presentation.dto.request.RescheduleRequest;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Controlador de citas para el usuario autenticado (no admin).
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // Crea una nueva cita/compra para el usuario autenticado.
    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse response = appointmentService.createAppointment(
                principal.getUser().getId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lista las citas del usuario autenticado.
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<AppointmentResponse> appointments = appointmentService.getMyAppointments(
                principal.getUser().getId()
        );
        return ResponseEntity.ok(appointments);
    }

    // Detalle de una cita propia (incluye meetingLink si ya fue asignado).
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getMyAppointment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getMyAppointment(principal.getUser().getId(), id));
    }

    // Reagenda una cita propia (penalización del 10% si faltan menos de 7 días).
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(appointmentService.reschedule(principal.getUser().getId(), id, request));
    }

    // Cancela una cita propia (penalización del 10% sobre el reembolso si faltan menos de 7 días).
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.cancel(principal.getUser().getId(), id, request));
    }

    // Reintenta el pago pendiente de una cita (tras un pago fallido o para cambiar de pasarela).
    @PostMapping("/{id}/payments")
    public ResponseEntity<AppointmentResponse> retryPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRetryRequest request) {
        AppointmentResponse response = appointmentService.retryPayment(principal.getUser().getId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
