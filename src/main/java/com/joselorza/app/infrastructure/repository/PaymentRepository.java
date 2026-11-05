package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    /**
     * Historial completo de pagos de una cita (reintentos incluidos).
     */
    List<Payment> findByAppointmentIdOrderByCreatedAtDesc(UUID appointmentId);

    /**
     * Último pago en un estado específico para una cita.
     * Usado para confirmación manual y para el job de expiración de pagos.
     */
    Optional<Payment> findFirstByAppointmentIdAndStatusOrderByCreatedAtDesc(
            UUID appointmentId, PaymentStatus status
    );

    /** Último pago de un tipo y estado (ej. la reserva COMPLETED sobre la que se calcula la penalidad). */
    Optional<Payment> findFirstByAppointmentIdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
            UUID appointmentId, PaymentType paymentType, PaymentStatus status
    );

    List<Payment> findByAppointmentIdAndStatus(UUID appointmentId, PaymentStatus status);

    boolean existsByAppointmentIdAndPaymentTypeAndStatus(
            UUID appointmentId, PaymentType paymentType, PaymentStatus status);

    /** Historial de pagos de un usuario. */
    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Bloqueo para procesar webhooks/confirmaciones de forma idempotente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    // --- Dashboard ---

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.status IN :statuses
        AND p.currency = :currency
        AND p.paidAt >= :from AND p.paidAt < :to
        """)
    BigDecimal sumPaidAmount(@Param("statuses") Collection<PaymentStatus> statuses,
                             @Param("currency") Currency currency,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    @Query("""
        SELECT COALESCE(SUM(p.refundedAmount), 0) FROM Payment p
        WHERE p.status = :status
        AND p.currency = :currency
        AND p.refundedAt >= :from AND p.refundedAt < :to
        """)
    BigDecimal sumRefundedAmount(@Param("status") PaymentStatus status,
                                 @Param("currency") Currency currency,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);

    long countByStatus(PaymentStatus status);
}
