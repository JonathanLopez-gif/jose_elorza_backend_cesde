package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Bloquea (pessimistic write) TODAS las citas activas en un instante exacto,
     * sin filtrar por servicio. Esto garantiza que el proveedor único no pueda
     * quedar agendado en dos servicios distintos al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.scheduledAt = :scheduledAt
        AND a.status IN :statuses
        """)
    List<Appointment> findActiveBookingsAtForUpdate(
            @Param("scheduledAt") Instant scheduledAt,
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
}