package com.joselorza.app.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Excepción puntual a las reglas de disponibilidad semanal.
 * Representa un día específico donde el proveedor bloquea su agenda
 * (ej. feriados, vacaciones, eventos). No puede abrir disponibilidad
 * fuera de los bloques definidos en AvailabilityRule.
 */
@Entity
@Table(name = "availability_exceptions",
        uniqueConstraints = @UniqueConstraint(columnNames = "date"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    /**
     * Reservado para extensibilidad futura. En la fase actual,
     * el sistema solo admite isAvailable = false (bloqueo de fecha).
     */
    @NotNull
    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = false;

    @Size(max = 255)
    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isAvailable == null) {
            this.isAvailable = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}