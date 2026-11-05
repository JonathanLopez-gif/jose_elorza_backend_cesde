package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AppointmentService;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.presentation.dto.request.CancelAppointmentRequest;
import com.joselorza.app.presentation.dto.request.MeetingLinkRequest;
import com.joselorza.app.presentation.dto.request.RescheduleRequest;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Controlador administrativo de citas: visibilidad total, solo ADMIN.
@RestController
@RequestMapping("/api/admin/appointments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    // Lista las citas del sistema con filtros opcionales (fechas en zona horaria del proveedor).
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID userId) {
        return ResponseEntity.ok(appointmentService.findAll(from, to, status, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    // Asigna o modifica el enlace de videoconferencia en cualquier momento.
    @PatchMapping("/{id}/meeting-link")
    public ResponseEntity<AppointmentResponse> updateMeetingLink(
            @PathVariable UUID id,
            @Valid @RequestBody MeetingLinkRequest request) {
        return ResponseEntity.ok(appointmentService.updateMeetingLink(id, request.meetingLink()));
    }

    // Reagendamiento manual (sin penalización).
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(appointmentService.adminReschedule(id, request));
    }

    // Cancelación por parte del admin (sin penalización).
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.adminCancel(id, request));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> markCompleted(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.markCompleted(id));
    }

    @PatchMapping("/{id}/no-show")
    public ResponseEntity<AppointmentResponse> markNoShow(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.markNoShow(id));
    }
}
