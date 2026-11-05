package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.GoogleCalendarService;
import com.joselorza.app.infrastructure.security.UserPrincipal;
import com.joselorza.app.presentation.dto.response.GoogleCalendarAuthorizationResponse;
import com.joselorza.app.presentation.dto.response.GoogleCalendarStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Gestión de la integración con Google Calendar desde el panel (solo ADMIN).
 * El administrador completa el flujo OAuth2 una sola vez.
 */
@RestController
@RequestMapping("/api/admin/google-calendar")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminGoogleCalendarController {

    private final GoogleCalendarService googleCalendarService;

    @GetMapping("/status")
    public ResponseEntity<GoogleCalendarStatusResponse> getStatus() {
        return ResponseEntity.ok(googleCalendarService.getStatus());
    }

    // Devuelve la URL de consentimiento de Google; el panel debe redirigir al admin a ella.
    @PostMapping("/authorize")
    public ResponseEntity<GoogleCalendarAuthorizationResponse> authorize(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(googleCalendarService.startAuthorization(principal.getUser().getId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        googleCalendarService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
