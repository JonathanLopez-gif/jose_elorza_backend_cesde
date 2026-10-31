package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

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
}