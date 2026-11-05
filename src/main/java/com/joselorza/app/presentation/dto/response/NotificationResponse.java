package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.NotificationChannel;
import com.joselorza.app.domain.model.enumerated.NotificationStatus;
import com.joselorza.app.domain.model.enumerated.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String userEmail,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        String payload,
        String errorMessage,
        Instant sentAt,
        Instant createdAt
) {
}
