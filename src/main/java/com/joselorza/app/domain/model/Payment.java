package com.joselorza.app.domain.model;

import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "uk_payments_gateway_transaction_id", columnList = "gateway_transaction_id", unique = true),
        @Index(name = "idx_payments_user", columnList = "user_id"),
        @Index(name = "idx_payments_appointment", columnList = "appointment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentGateway gateway;

    @Column(name = "gateway_transaction_id", length = 255)
    private String gatewayTransactionId;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // Incluye IVA

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    // Enlace de checkout de la pasarela (Wompi / Global66); null para pagos manuales
    @Column(name = "checkout_url", length = 2000)
    private String checkoutUrl;

    private Instant paidAt; // Momento en que el pago pasó a COMPLETED

    // --- Reembolso manual (la devolución del dinero se gestiona fuera de la plataforma) ---
    @Column(precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    @Column(columnDefinition = "TEXT")
    private String refundReason;

    private Instant refundedAt;

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
