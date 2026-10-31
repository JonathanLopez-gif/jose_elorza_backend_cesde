package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.PaymentService;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(@PathVariable UUID id) {
        PaymentResponse response = paymentService.confirmManualPayment(id);
        return ResponseEntity.ok(response);
    }
}