package com.joselorza.app.domain.model;

import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.SessionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representa un plan/servicio ofrecido en el catálogo.
 * Los precios se manejan en dos monedas (COP y USD) para que el cliente
 * elija la moneda al momento de reservar.
 */
@Entity
@Table(name = "service_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Min(1)
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "price_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceCop;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "price_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceUsd;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modality modality;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private SessionType sessionType;

    // Null para INDIVIDUAL; entre 2 y 10 para GROUP (validado en el servicio).
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Builder.Default
    @Column(name = "tax_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal taxRate = new BigDecimal("0.19");

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}