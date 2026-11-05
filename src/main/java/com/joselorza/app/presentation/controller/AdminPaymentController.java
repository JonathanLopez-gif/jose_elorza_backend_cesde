package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.PaymentService;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.presentation.dto.request.RefundRequest;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    // Listado de pagos con filtros por fecha (de creación), estado, pasarela y usuario.
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentGateway gateway,
            @RequestParam(required = false) UUID userId) {
        return ResponseEntity.ok(paymentService.findAll(from, to, status, gateway, userId));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(@PathVariable UUID id) {
        PaymentResponse response = paymentService.confirmManualPayment(id);
        return ResponseEntity.ok(response);
    }

    // Marca el reembolso (registro interno + notificación); el dinero se devuelve fuera de la plataforma.
    @PatchMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(id, request));
    }
}
