package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.AvailabilityException;
import com.joselorza.app.domain.model.AvailabilityRule;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.SessionType;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityExceptionRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reglas de validación de horarios compartidas por creación, reagendamiento
 * (usuario/admin), consulta pública de horarios y aplicación de reprogramaciones
 * tardías tras pagar la penalidad.
 *
 * Los choques se detectan por solapamiento de duración (no solo por instante exacto):
 * el proveedor es único, así que dos citas no pueden cruzarse en el tiempo, salvo
 * reservas GROUP del mismo servicio que comparten exactamente el mismo horario.
 */
@Component
@RequiredArgsConstructor
public class AppointmentSlotValidator {

    static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CONFIRMED);

    // Ninguna sesión dura más de esto: se usa para buscar citas que empiezan antes y aún siguen en curso
    private static final Duration MAX_SESSION_LOOKBACK = Duration.ofHours(24);

    private final AppointmentRepository appointmentRepository;
    private final AvailabilityRuleRepository availabilityRuleRepository;
    private final AvailabilityExceptionRepository availabilityExceptionRepository;
    private final SchedulingProperties schedulingProperties;

    /**
     * Franja ocupada en la agenda del proveedor. Una cita con reprogramación tardía
     * pendiente ocupa dos franjas: la actual y la nueva (reservada mientras paga).
     */
    public record Commitment(UUID appointmentId, UUID servicePlanId, Instant start, Instant end, UUID groupKey) {
    }

    /**
     * Valida que la cita completa (inicio + duración) caiga dentro de un bloque de
     * disponibilidad activo, que la fecha no esté bloqueada por una excepción puntual
     * y que se respete la anticipación mínima.
     * Las reglas se interpretan en la zona horaria del proveedor; las citas llegan en UTC.
     */
    public void validateAvailability(ServicePlan servicePlan, Instant scheduledAt) {
        Instant minimum = Instant.now().plus(Duration.ofHours(schedulingProperties.getMinNoticeHours()));
        if (scheduledAt.isBefore(minimum)) {
            throw new BusinessRuleException(BusinessRuleException.SLOT_UNAVAILABLE,
                    "Appointments must be booked at least " + schedulingProperties.getMinNoticeHours()
                            + " hours in advance");
        }

        ZoneId providerZone = schedulingProperties.providerZone();
        ZonedDateTime start = scheduledAt.atZone(providerZone);
        LocalDate date = start.toLocalDate();
        LocalTime startTime = start.toLocalTime();

        boolean isBlocked = availabilityExceptionRepository.findByDate(date)
                .map(AvailabilityException::getIsAvailable)
                .map(available -> !Boolean.TRUE.equals(available))
                .orElse(false);

        if (isBlocked) {
            throw new BusinessRuleException(BusinessRuleException.SLOT_UNAVAILABLE,
                    "The selected date is not available");
        }

        ZonedDateTime end = start.plusMinutes(servicePlan.getDurationMinutes());
        // Si la sesión termina en otro día, se toma como fin de jornada (bloques no cruzan medianoche)
        LocalTime endTime = end.toLocalDate().isAfter(date) ? LocalTime.MAX : end.toLocalTime();

        int dayOfWeek = date.getDayOfWeek().getValue(); // ISO: 1=lunes .. 7=domingo

        List<AvailabilityRule> rules =
                availabilityRuleRepository.findByDayOfWeekAndIsAvailableTrueOrderByStartTimeAsc(dayOfWeek);

        boolean withinBlock = rules.stream().anyMatch(rule ->
                !startTime.isBefore(rule.getStartTime())
                        && startTime.isBefore(rule.getEndTime())
                        && !endTime.isAfter(rule.getEndTime()));

        if (!withinBlock) {
            throw new BusinessRuleException(BusinessRuleException.SLOT_UNAVAILABLE,
                    "The selected schedule is not available");
        }
    }

    /**
     * Verifica que el proveedor no tenga compromisos que se crucen con la nueva cita
     * y resuelve el groupKey a utilizar:
     * - Si no hay nada que se cruce: se permite (GROUP genera groupKey nuevo).
     * - Si se cruza con otro servicio u otro horario: se rechaza (proveedor ocupado).
     * - Si coincide exactamente con el MISMO servicio y horario:
     *     - INDIVIDUAL: ya está ocupado, se rechaza.
     *     - GROUP: se valida capacidad y se reutiliza el groupKey existente.
     *
     * @param excludeAppointmentId cita a ignorar (la propia cita al reagendar); puede ser null
     */
    public UUID validateCapacityAndResolveGroupKey(ServicePlan servicePlan, Instant scheduledAt,
                                                   UUID excludeAppointmentId) {
        Instant end = scheduledAt.plus(Duration.ofMinutes(servicePlan.getDurationMinutes()));
        List<Commitment> overlapping = overlapping(
                loadCommitments(scheduledAt, end, true), scheduledAt, end, excludeAppointmentId);

        if (overlapping.isEmpty()) {
            return servicePlan.getSessionType() == SessionType.GROUP
                    ? UUID.randomUUID()
                    : null;
        }

        boolean sameServiceAndTime = overlapping.stream().allMatch(c ->
                c.servicePlanId().equals(servicePlan.getId()) && c.start().equals(scheduledAt));

        if (!sameServiceAndTime) {
            throw new BusinessRuleException(BusinessRuleException.SLOT_UNAVAILABLE,
                    "The provider already has an appointment scheduled at that time");
        }

        if (servicePlan.getSessionType() == SessionType.INDIVIDUAL) {
            throw new BusinessRuleException(BusinessRuleException.SLOT_UNAVAILABLE,
                    "The selected time slot has already been booked");
        }

        if (overlapping.size() >= servicePlan.getMaxCapacity()) {
            throw new BusinessRuleException(BusinessRuleException.SLOT_UNAVAILABLE,
                    "Maximum capacity reached for this time slot");
        }

        // Solo se reutiliza el groupKey de citas que ya están en ese instante
        // (las reprogramaciones pendientes aún conservan el groupKey de su horario anterior)
        return overlapping.stream()
                .map(Commitment::groupKey)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(UUID.randomUUID());
    }

    /**
     * Cupos restantes de un servicio en un horario, según los compromisos ya cargados.
     * 0 significa que el horario no se puede reservar.
     */
    public int remainingCapacity(ServicePlan servicePlan, Instant start, List<Commitment> commitments) {
        Instant end = start.plus(Duration.ofMinutes(servicePlan.getDurationMinutes()));
        List<Commitment> overlapping = overlapping(commitments, start, end, null);
        int capacity = servicePlan.getSessionType() == SessionType.GROUP ? servicePlan.getMaxCapacity() : 1;

        if (overlapping.isEmpty()) {
            return capacity;
        }

        boolean sameServiceAndTime = overlapping.stream().allMatch(c ->
                c.servicePlanId().equals(servicePlan.getId()) && c.start().equals(start));

        if (!sameServiceAndTime || servicePlan.getSessionType() == SessionType.INDIVIDUAL) {
            return 0;
        }
        return Math.max(0, capacity - overlapping.size());
    }

    /**
     * Carga los compromisos activos que pueden cruzarse con el rango [from, to).
     */
    public List<Commitment> loadCommitments(Instant from, Instant to, boolean lock) {
        Instant searchFrom = from.minus(MAX_SESSION_LOOKBACK);
        List<Appointment> appointments = lock
                ? appointmentRepository.findActiveCommitmentsForUpdate(searchFrom, to, ACTIVE_STATUSES)
                : appointmentRepository.findActiveCommitments(searchFrom, to, ACTIVE_STATUSES);

        List<Commitment> commitments = new ArrayList<>();
        for (Appointment a : appointments) {
            Duration duration = Duration.ofMinutes(a.getDurationMinutes());
            commitments.add(new Commitment(a.getId(), a.getServicePlan().getId(),
                    a.getScheduledAt(), a.getScheduledAt().plus(duration), a.getGroupKey()));
            if (a.getPendingRescheduledAt() != null) {
                commitments.add(new Commitment(a.getId(), a.getServicePlan().getId(),
                        a.getPendingRescheduledAt(), a.getPendingRescheduledAt().plus(duration), null));
            }
        }
        return commitments;
    }

    /**
     * Indica si una cita está dentro de la ventana de penalización (menos de N días para su inicio).
     */
    public boolean isWithinPenaltyWindow(Instant scheduledAt) {
        Instant threshold = Instant.now().plus(Duration.ofDays(schedulingProperties.getPenaltyThresholdDays()));
        return scheduledAt.isBefore(threshold);
    }

    private List<Commitment> overlapping(List<Commitment> commitments, Instant start, Instant end, UUID excludeId) {
        return commitments.stream()
                .filter(c -> excludeId == null || !c.appointmentId().equals(excludeId))
                .filter(c -> c.start().isBefore(end) && c.end().isAfter(start))
                .toList();
    }
}
