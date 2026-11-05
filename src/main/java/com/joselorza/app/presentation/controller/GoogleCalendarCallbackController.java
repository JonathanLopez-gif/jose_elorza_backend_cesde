package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Callback OAuth2 de Google. Es público porque lo invoca el navegador al volver
 * de Google (sin JWT); se protege con el parámetro "state" de un solo uso.
 */
@RestController
@RequestMapping("/api/google-calendar/oauth2")
@RequiredArgsConstructor
public class GoogleCalendarCallbackController {

    private final GoogleCalendarService googleCalendarService;

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String redirect = googleCalendarService.completeAuthorization(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect)).build();
    }
}
