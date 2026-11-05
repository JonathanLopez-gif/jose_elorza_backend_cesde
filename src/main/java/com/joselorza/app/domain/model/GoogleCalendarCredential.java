package com.joselorza.app.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Credencial OAuth2 persistente del administrador para Google Calendar.
 * Es un token único del sistema: solo existe una fila activa.
 */
@Entity
@Table(name = "google_calendar_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleCalendarCredential {

    @Id
    @GeneratedValue
    private UUID id;

    // Refresh token cifrado con AES-GCM (nunca en texto plano)
    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "calendar_id", nullable = false)
    private String calendarId;

    // Admin que completó el flujo OAuth2
    @Column(name = "connected_by")
    private UUID connectedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
