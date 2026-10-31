package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AppointmentService;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Controlador administrativo de citas: visibilidad total, solo ADMIN.
@RestController
@RequestMapping("/api/admin/appointments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    // Lista todas las citas del sistema (todos los usuarios).
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }
}