package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.NotificationService;
import com.joselorza.app.domain.model.enumerated.NotificationStatus;
import com.joselorza.app.domain.model.enumerated.NotificationType;
import com.joselorza.app.presentation.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Log de envíos de Email (auditoría de notificaciones).
@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationStatus status) {
        return ResponseEntity.ok(notificationService.findAll(userId, type, status));
    }
}
