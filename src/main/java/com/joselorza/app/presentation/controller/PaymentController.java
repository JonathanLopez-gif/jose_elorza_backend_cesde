package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.PaymentService;
import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.infrastructure.security.UserPrincipal;
import com.joselorza.app.presentation.dto.request.SandboxResultRequest;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Pagos del usuario autenticado: historial, detalle y comprobantes PDF.
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/me")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.getMyPayments(principal.getUser().getId()));
    }

    // Detalle de un pago (el frontend lo consulta al volver de la pasarela).
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id, principal.getUser().getId(), isAdmin(principal)));
    }

    // Comprobante PDF con IVA desglosado (el admin puede descargar cualquiera).
    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PaymentService.ReceiptFile receipt =
                paymentService.generateReceipt(id, principal.getUser().getId(), isAdmin(principal));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.fileName()).build().toString())
                .body(receipt.content());
    }

    // Solo desarrollo: la página simuladora de pasarela informa el resultado (404 si el simulador está apagado).
    @PostMapping("/{id}/sandbox-result")
    public ResponseEntity<PaymentResponse> sandboxResult(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SandboxResultRequest request) {
        return ResponseEntity.ok(paymentService.applySandboxResult(id, principal.getUser().getId(), request.result()));
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getUser().getRole() == Role.ADMIN;
    }
}
