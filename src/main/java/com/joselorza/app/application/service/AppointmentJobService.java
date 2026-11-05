package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.NotificationType;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de los jobs programados de agenda. Cada cita se procesa en su propia
 * transacción para que un error puntual no afecte al resto del lote.
 */
@Slf4j
@Service
public class AppointmentJobService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final NotificationService notificationService;
    private final SchedulingProperties schedulingProperties;
    private final TransactionTemplate transactionTemplate;

    public AppointmentJobService(AppointmentRepository appointmentRepository,
                                 AppointmentService appointmentService,
                                 NotificationService notificationService,
                                 SchedulingProperties schedulingProperties,
                                 PlatformTransactionManager transactionManager) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.notificationService = notificationService;
        this.schedulingProperties = schedulingProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Recordatorio por Email 24h antes (un único recordatorio por cita).
     */
    public int sendReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(Duration.ofHours(schedulingProperties.getReminderHoursBefore()));

        List<UUID> ids = appointmentRepository
                .findByStatusAndReminderSentFalseAndScheduledAtBetween(AppointmentStatus.CONFIRMED, now, windowEnd)
                .stream().map(Appointment::getId).toList();

        int processed = 0;
        for (UUID id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Appointment appointment = appointmentRepository.findById(id).orElseThrow();
                    // Se marca antes de enviar: sin reintentos ni recordatorios adicionales
                    appointment.setReminderSent(true);
                    appointmentRepository.save(appointment);
                });
                notificationService.notifyAppointment(id, NotificationType.REMINDER);
                processed++;
            } catch (Exception e) {
                log.error("Error sending reminder for appointment {}", id, e);
            }
        }
        return processed;
    }

    /**
     * Marca como NO_SHOW las citas confirmadas cuya hora ya pasó (más un margen)
     * sin que el usuario cancelara ni el admin la marcara como completada.
     */
    public int markNoShows() {
        Instant now = Instant.now();
        Duration grace = Duration.ofHours(schedulingProperties.getNoShowGraceHours());

        List<UUID> ids = appointmentRepository.findByStatusAndScheduledAtBefore(AppointmentStatus.CONFIRMED, now)
                .stream()
                .filter(a -> a.getScheduledAt()
                        .plus(Duration.ofMinutes(a.getDurationMinutes()))
                        .plus(grace)
                        .isBefore(now))
                .map(Appointment::getId)
                .toList();

        int processed = 0;
        for (UUID id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Appointment appointment = appointmentRepository.findById(id).orElseThrow();
                    if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
                        appointmentService.markAsNoShow(appointment);
                    }
                });
                processed++;
            } catch (Exception e) {
                log.error("Error marking appointment {} as NO_SHOW", id, e);
            }
        }
        return processed;
    }

    /**
     * Cancela citas cuyo plazo de pago (reserva o penalidad) venció.
     */
    public int expireOverduePayments() {
        Instant now = Instant.now();

        List<UUID> ids = new java.util.ArrayList<>();
        appointmentRepository.findByStatusAndPaymentDueAtBefore(AppointmentStatus.PENDING_PAYMENT, now)
                .forEach(a -> ids.add(a.getId()));
        appointmentRepository.findByStatusAndPaymentDueAtBefore(AppointmentStatus.CONFIRMED, now).stream()
                .filter(a -> a.getPendingRescheduledAt() != null)
                .forEach(a -> ids.add(a.getId()));

        int processed = 0;
        for (UUID id : ids) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Appointment appointment = appointmentRepository.findById(id).orElseThrow();
                    if (appointment.getPaymentDueAt() != null && appointment.getPaymentDueAt().isBefore(Instant.now())) {
                        appointmentService.expireOverduePayment(appointment);
                    }
                });
                processed++;
            } catch (Exception e) {
                log.error("Error expiring payment deadline of appointment {}", id, e);
            }
        }
        return processed;
    }
}
