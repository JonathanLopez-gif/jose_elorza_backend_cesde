package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    /**
     * Bloquea (pessimistic write) TODAS las citas activas que inician dentro de un rango,
     * sin filtrar por servicio. Con esto se detectan solapamientos por duración y se
     * garantiza que el proveedor único no quede agendado en dos citas a la vez.
     * También considera las citas con una reprogramación tardía pendiente hacia
     * ese rango (el horario queda reservado mientras se paga la penalidad).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status IN :statuses
        AND ((a.scheduledAt >= :from AND a.scheduledAt < :to)
          OR (a.pendingRescheduledAt >= :from AND a.pendingRescheduledAt < :to))
        """)
    List<Appointment> findActiveCommitmentsForUpdate(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("statuses") List<AppointmentStatus> statuses);

    /**
     * Misma consulta sin bloqueo, para pintar la ocupación en la consulta pública de horarios.
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status IN :statuses
        AND ((a.scheduledAt >= :from AND a.scheduledAt < :to)
          OR (a.pendingRescheduledAt >= :from AND a.pendingRescheduledAt < :to))
        """)
    List<Appointment> findActiveCommitments(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("statuses") List<AppointmentStatus> statuses);

    /**
     * Citas que comparten el mismo groupKey (reservas GROUP del mismo
     * servicio/horario). Útil para operaciones futuras sobre el grupo completo.
     */
    List<Appointment> findByGroupKey(UUID groupKey);

    /**
     * Listado de citas de un usuario, ordenadas por fecha descendente.
     */
    List<Appointment> findByUserIdOrderByScheduledAtDesc(UUID userId);

    Optional<Appointment> findByIdAndUserId(UUID id, UUID userId);

    /** Cantidad de citas por usuario (listado administrativo de usuarios). */
    @Query("SELECT a.user.id, COUNT(a) FROM Appointment a GROUP BY a.user.id")
    List<Object[]> countGroupedByUser();

    // --- Jobs programados ---

    /** Recordatorio 24h: citas confirmadas que inician dentro de la ventana y aún no se notificaron. */
    List<Appointment> findByStatusAndReminderSentFalseAndScheduledAtBetween(
            AppointmentStatus status, Instant from, Instant to);

    /** NO_SHOW: citas confirmadas cuya hora de inicio ya pasó. */
    List<Appointment> findByStatusAndScheduledAtBefore(AppointmentStatus status, Instant before);

    /** Expiración de pagos: reservas o penalidades cuyo plazo de pago venció. */
    List<Appointment> findByStatusAndPaymentDueAtBefore(AppointmentStatus status, Instant before);

    // --- Dashboard ---

    long countByScheduledAtBetweenAndStatusIn(Instant from, Instant to, Collection<AppointmentStatus> statuses);

    long countByStatus(AppointmentStatus status);

    List<Appointment> findByScheduledAtBetweenAndStatusIn(Instant from, Instant to, Collection<AppointmentStatus> statuses);

    List<Appointment> findTop6ByStatusAndScheduledAtAfterOrderByScheduledAtAsc(AppointmentStatus status, Instant after);

    @Query("SELECT a.status, COUNT(a) FROM Appointment a GROUP BY a.status")
    List<Object[]> countGroupedByStatus();
}
