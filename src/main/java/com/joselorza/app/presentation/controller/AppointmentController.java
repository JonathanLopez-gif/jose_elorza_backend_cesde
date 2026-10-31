package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AppointmentService;
import com.joselorza.app.infrastructure.security.UserPrincipal;
import com.joselorza.app.presentation.dto.request.AppointmentRequest;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}