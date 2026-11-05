package com.joselorza.app.domain.model;

import com.joselorza.app.domain.model.enumerated.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // Hash BCrypt

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Zona horaria IANA del usuario, ej: "America/Bogota"
    @Column(nullable = false)
    private String timezone;

    @Column
    private String phone; // Opcional

    // Preferencia de notificaciones por email
    @Column(name = "notify_email", nullable = false)
    @Builder.Default
    private boolean notifyEmail = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // Evidencia de aceptación de términos de uso y política de privacidad (Ley 1581 / GDPR)
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
