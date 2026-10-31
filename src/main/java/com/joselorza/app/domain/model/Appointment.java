package com.joselorza.app.domain.model;

import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_plan_id", nullable = false)
    private ServicePlan servicePlan;

    @NotNull
    @Column(nullable = false)
    private Instant scheduledAt; // Siempre almacenado en UTC

    @NotNull
    @Column(nullable = false)
    private Integer durationMinutes; // Copiado del ServicePlan al momento de reservar

    @NotBlank
    @Column(nullable = false)
    private String userTimezone; // Para presentar la hora al usuario

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(length = 500)
    private String meetingLink; // Solo aplica a LIVE y ONLINE

    @Column(name = "group_key")
    private UUID groupKey; // Agrupa reservas GROUP del mismo servicio/horario

    @Builder.Default
    @Column(nullable = false)
    private Boolean penaltyApplied = false;

    @Column(precision = 12, scale = 2)
    private BigDecimal penaltyAmount;

    private Instant paymentDueAt; // Límite de 48h (pago inicial o penalidad)

    private Instant pendingRescheduledAt; // Nueva fecha pendiente de penalidad en reprogramación tardía

    @Builder.Default
    @Column(nullable = false)
    private Boolean reminderSent = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}