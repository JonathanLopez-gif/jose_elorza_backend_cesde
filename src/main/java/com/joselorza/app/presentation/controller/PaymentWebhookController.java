package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.PaymentService;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

/**
 * Webhooks públicos de las pasarelas. La autenticidad se valida con la firma
 * de cada pasarela (no con JWT). Se recibe el cuerpo crudo para validar la firma
 * exactamente sobre los bytes enviados.
 */
@RestController
@RequestMapping("/api/payments/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/wompi")
    public ResponseEntity<Void> wompiWebhook(@RequestBody String payload, @RequestHeader HttpHeaders headers) {
        paymentService.handleWebhook(PaymentGateway.WOMPI, payload, toMap(headers));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/global66")
    public ResponseEntity<Void> global66Webhook(@RequestBody String payload, @RequestHeader HttpHeaders headers) {
        paymentService.handleWebhook(PaymentGateway.GLOBAL66, payload, toMap(headers));
        return ResponseEntity.ok().build();
    }

    // Headers sin distinción de mayúsculas/minúsculas
    private Map<String, String> toMap(HttpHeaders headers) {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                map.put(name, values.get(0));
            }
        });
        return map;
    }
}
