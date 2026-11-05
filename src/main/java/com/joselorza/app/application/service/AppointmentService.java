package com.joselorza.app.application.service;

import com.joselorza.app.application.event.AppointmentEvent;
import com.joselorza.app.application.event.AppointmentEventType;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.presentation.dto.request.AppointmentRequest;
import com.joselorza.app.presentation.dto.request.CancelAppointmentRequest;
import com.joselorza.app.presentation.dto.request.PaymentRetryRequest;
import com.joselorza.app.presentation.dto.request.RescheduleRequest;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import com.joselorza.app.presentation.mapper.AppointmentMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final ServicePlanRepository servicePlanRepository;
    private final UserRepository userRepository;
    private final AppointmentSlotValidator slotValidator;
    private final PaymentService paymentService;
    private final SchedulingProperties schedulingProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final AppointmentMapper appointmentMapper;

    // ===============================================================
    // Operaciones del usuario
    // ===============================================================

    @Transactional
    public AppointmentResponse createAppointment(UUID userId, AppointmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException());

        ServicePlan servicePlan = servicePlanRepository.findById(request.servicePlanId())
                .orElseThrow(() -> new ResourceNotFoundException());

        if (!Boolean.TRUE.equals(servicePlan.getIsActive())) {
            throw new BusinessRuleException("The selected service isn't available");
        }

        boolean isRecorded = servicePlan.getModality() == Modality.RECORDED;

        Instant scheduledAt;
        UUID groupKey = null;

        if (isRecorded) {
            // Compra directa: no requiere franja horaria ni validación de disponibilidad.
            scheduledAt = Instant.now();
        } else {
            if (request.scheduledAt() == null) {
                throw new BusinessRuleException("The date and time are required for this type of service");
            }
            scheduledAt = request.scheduledAt();

            slotValidator.validateAvailability(servicePlan, scheduledAt);
            groupKey = slotValidator.validateCapacityAndResolveGroupKey(servicePlan, scheduledAt, null);
        }

        Appointment appointment = Appointment.builder()
                .user(user)
                .servicePlan(servicePlan)
                .scheduledAt(scheduledAt)
                .durationMinutes(servicePlan.getDurationMinutes())
                .userTimezone(user.getTimezone())
                .status(AppointmentStatus.PENDING_PAYMENT)
                .meetingLink(null) // se asigna manualmente más adelante (LIVE/ONLINE)
                .groupKey(groupKey)
                .penaltyApplied(false)
                .reminderSent(false)
                .paymentDueAt(paymentDeadline())
                .build();

        appointment = appointmentRepository.save(appointment);

        Payment payment = paymentService.createPayment(
                appointment,
                PaymentType.BOOKING,
                calculateBookingAmount(servicePlan, request.currency()),
                request.currency(),
                request.gateway());

        return appointmentMapper.toResponse(appointment, payment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments(UUID userId) {
        return appointmentRepository.findByUserIdOrderByScheduledAtDesc(userId).stream()
                .map(appointment -> appointmentMapper.toResponse(appointment, latestPaymentOf(appointment)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getMyAppointment(UUID userId, UUID appointmentId) {
        Appointment appointment = findOwned(userId, appointmentId);
        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    /**
     * Reagendamiento solicitado por el usuario (sin límite de veces):
     * - Cita aún sin pagar: se mueve directamente.
     * - Con ≥ 7 días de anticipación: se mantiene el pago original.
     * - Con < 7 días: se genera un pago de penalización del 10%; la nueva fecha queda
     *   reservada y se aplica al confirmarse el pago. Si no se paga en el plazo,
     *   la cita se cancela (job de expiración) y debe reservarse una nueva.
     */
    @Transactional
    public AppointmentResponse reschedule(UUID userId, UUID appointmentId, RescheduleRequest request) {
        Appointment appointment = findOwned(userId, appointmentId);
        validateReschedulable(appointment);

        if (appointment.getPendingRescheduledAt() != null) {
            throw new BusinessRuleException(
                    "There is already a pending reschedule waiting for the penalty payment");
        }

        Instant newScheduledAt = request.scheduledAt();
        if (newScheduledAt.equals(appointment.getScheduledAt())) {
            throw new BusinessRuleException("The new date must be different from the current one");
        }

        ServicePlan servicePlan = appointment.getServicePlan();
        slotValidator.validateAvailability(servicePlan, newScheduledAt);
        UUID groupKey = slotValidator.validateCapacityAndResolveGroupKey(servicePlan, newScheduledAt, appointment.getId());

        boolean isConfirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;

        if (isConfirmed && slotValidator.isWithinPenaltyWindow(appointment.getScheduledAt())) {
            Payment bookingPayment = findCompletedBookingPayment(appointment);
            BigDecimal penalty = calculatePenalty(bookingPayment.getAmount());

            appointment.setPendingRescheduledAt(newScheduledAt);
            appointment.setPenaltyApplied(true);
            appointment.setPenaltyAmount(penalty);
            appointment.setPaymentDueAt(paymentDeadline());
            appointmentRepository.save(appointment);

            PaymentGateway gateway = request.gateway() != null ? request.gateway() : bookingPayment.getGateway();
            Payment penaltyPayment = paymentService.createPayment(
                    appointment, PaymentType.PENALTY, penalty, bookingPayment.getCurrency(), gateway);

            return appointmentMapper.toResponse(appointment, penaltyPayment);
        }

        applyReschedule(appointment, newScheduledAt, groupKey);

        if (isConfirmed) {
            eventPublisher.publishEvent(new AppointmentEvent(appointment.getId(), AppointmentEventType.RESCHEDULED));
        }

        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    /**
     * Cancelación solicitada por el usuario. Con < 7 días de anticipación se registra
     * una penalización del 10% que se descuenta del reembolso (lo gestiona el admin).
     */
    @Transactional
    public AppointmentResponse cancel(UUID userId, UUID appointmentId, CancelAppointmentRequest request) {
        Appointment appointment = findOwned(userId, appointmentId);
        validateCancellable(appointment);

        boolean wasConfirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;
        boolean applyPenalty = wasConfirmed && slotValidator.isWithinPenaltyWindow(appointment.getScheduledAt());

        BigDecimal penalty = applyPenalty
                ? calculatePenalty(findCompletedBookingPayment(appointment).getAmount())
                : null;

        cancelInternal(appointment, request == null ? null : request.reason(), penalty);

        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    /**
     * Reintento de pago (tras un pago fallido o para cambiar de pasarela) de la
     * obligación pendiente de la cita: la reserva o la penalidad de reagendamiento.
     */
    @Transactional
    public AppointmentResponse retryPayment(UUID userId, UUID appointmentId, PaymentRetryRequest request) {
        Appointment appointment = findOwned(userId, appointmentId);

        if (appointment.getPaymentDueAt() != null && appointment.getPaymentDueAt().isBefore(Instant.now())) {
            throw new BusinessRuleException(BusinessRuleException.HOLD_EXPIRED,
                    "The payment deadline for this appointment has expired");
        }

        PaymentType paymentType;
        BigDecimal amount;
        Currency currency;

        if (appointment.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            paymentType = PaymentType.BOOKING;
            currency = request.currency() != null
                    ? request.currency()
                    : latestPaymentCurrency(appointment);
            amount = calculateBookingAmount(appointment.getServicePlan(), currency);
        } else if (appointment.getStatus() == AppointmentStatus.CONFIRMED && appointment.getPendingRescheduledAt() != null) {
            paymentType = PaymentType.PENALTY;
            amount = appointment.getPenaltyAmount();
            currency = findCompletedBookingPayment(appointment).getCurrency(); // La penalidad se paga en la moneda original
        } else {
            throw new BusinessRuleException(BusinessRuleException.HOLD_EXPIRED, "The appointment has no pending payments");
        }

        // Solo un intento pendiente a la vez: los anteriores quedan como FAILED
        paymentService.failPendingPayments(appointment.getId());

        Payment payment = paymentService.createPayment(appointment, paymentType, amount, currency, request.gateway());

        return appointmentMapper.toResponse(appointment, payment);
    }

    // ===============================================================
    // Operaciones administrativas
    // ===============================================================

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        return findAll(null, null, null, null);
    }

    /**
     * Listado administrativo con filtros opcionales (fechas en zona horaria del proveedor).
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findAll(LocalDate from, LocalDate to, AppointmentStatus status, UUID userId) {
        return findEntities(DateRange.of(from, to, schedulingProperties.providerZone()), status, userId).stream()
                .map(appointment -> appointmentMapper.toResponse(appointment, latestPaymentOf(appointment)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Appointment> findEntities(DateRange range, AppointmentStatus status, UUID userId) {
        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (range.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), range.from()));
            }
            if (range.to() != null) {
                predicates.add(cb.lessThan(root.get("scheduledAt"), range.to()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return appointmentRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "scheduledAt"));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(UUID appointmentId) {
        Appointment appointment = findOrThrow(appointmentId);
        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    /**
     * Asigna o modifica el enlace de videoconferencia en cualquier momento.
     * Queda visible para el usuario de inmediato y se incluye en los correos siguientes.
     */
    @Transactional
    public AppointmentResponse updateMeetingLink(UUID appointmentId, String meetingLink) {
        Appointment appointment = findOrThrow(appointmentId);

        Modality modality = appointment.getServicePlan().getModality();
        if (modality == Modality.RECORDED || modality == Modality.IN_PERSON) {
            throw new BusinessRuleException("The meeting link only applies to remote sessions (LIVE / ONLINE)");
        }

        appointment.setMeetingLink(meetingLink == null || meetingLink.isBlank() ? null : meetingLink.trim());
        appointmentRepository.save(appointment);

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            eventPublisher.publishEvent(
                    new AppointmentEvent(appointment.getId(), AppointmentEventType.MEETING_LINK_UPDATED));
        }

        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    /**
     * Reagendamiento manual del admin: sin penalización y sin exigir que el horario
     * esté dentro de las reglas de disponibilidad (solo se valida que no haya choques).
     */
    @Transactional
    public AppointmentResponse adminReschedule(UUID appointmentId, RescheduleRequest request) {
        Appointment appointment = findOrThrow(appointmentId);
        validateReschedulable(appointment);

        Instant newScheduledAt = request.scheduledAt();
        UUID groupKey = slotValidator.validateCapacityAndResolveGroupKey(
                appointment.getServicePlan(), newScheduledAt, appointment.getId());

        // Si había una reprogramación tardía pendiente de pago, el admin la reemplaza
        if (appointment.getPendingRescheduledAt() != null) {
            paymentService.failPendingPayments(appointment.getId());
            appointment.setPendingRescheduledAt(null);
            appointment.setPaymentDueAt(null);
            appointment.setPenaltyApplied(false);
            appointment.setPenaltyAmount(null);
        }

        applyReschedule(appointment, newScheduledAt, groupKey);

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            eventPublisher.publishEvent(new AppointmentEvent(appointment.getId(), AppointmentEventType.RESCHEDULED));
        }

        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    /**
     * Cancelación por parte del admin: sin penalización (reembolso completo sugerido).
     */
    @Transactional
    public AppointmentResponse adminCancel(UUID appointmentId, CancelAppointmentRequest request) {
        Appointment appointment = findOrThrow(appointmentId);
        validateCancellable(appointment);

        cancelInternal(appointment, request == null ? null : request.reason(), null);

        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    @Transactional
    public AppointmentResponse markCompleted(UUID appointmentId) {
        Appointment appointment = findOrThrow(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED && appointment.getStatus() != AppointmentStatus.NO_SHOW) {
            throw new BusinessRuleException(
                    "Only confirmed or no-show appointments can be marked as completed. Current status: "
                            + appointment.getStatus());
        }

        if (appointment.getScheduledAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("The appointment hasn't started yet");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    @Transactional
    public AppointmentResponse markNoShow(UUID appointmentId) {
        Appointment appointment = findOrThrow(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Only confirmed appointments can be marked as no-show. Current status: " + appointment.getStatus());
        }

        if (appointment.getScheduledAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("The appointment hasn't started yet");
        }

        markAsNoShow(appointment);
        return appointmentMapper.toResponse(appointment, latestPaymentOf(appointment));
    }

    // ===============================================================
    // Lógica compartida con los jobs programados
    // ===============================================================

    /**
     * Cita no asistida sin cancelar ni reagendar: NO_SHOW, sin reembolso.
     */
    @Transactional
    public void markAsNoShow(Appointment appointment) {
        if (appointment.getPendingRescheduledAt() != null) {
            paymentService.failPendingPayments(appointment.getId());
            appointment.setPendingRescheduledAt(null);
            appointment.setPaymentDueAt(null);
        }
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);
    }

    /**
     * Plazo de pago vencido:
     * - Reserva sin pagar: se cancela la cita.
     * - Penalidad de reagendamiento tardío sin pagar: se cancela la cita (aplica la
     *   penalización sobre el reembolso) y el usuario debe reservar una nueva con nuevo pago.
     */
    @Transactional
    public void expireOverduePayment(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            cancelInternal(appointment, "Payment deadline expired", null);
        } else if (appointment.getStatus() == AppointmentStatus.CONFIRMED && appointment.getPendingRescheduledAt() != null) {
            cancelInternal(appointment, "Penalty payment deadline expired", appointment.getPenaltyAmount());
        }
    }

    // ===============================================================
    // Métodos privados de apoyo
    // ===============================================================

    private void cancelInternal(Appointment appointment, String reason, BigDecimal penalty) {
        boolean wasConfirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;

        paymentService.failPendingPayments(appointment.getId());

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(Instant.now());
        appointment.setCancellationReason(reason);
        appointment.setPendingRescheduledAt(null);
        appointment.setPaymentDueAt(null);
        appointment.setPenaltyApplied(penalty != null);
        appointment.setPenaltyAmount(penalty);
        appointmentRepository.save(appointment);

        // Solo se notifica/sincroniza si la cita llegó a estar confirmada
        if (wasConfirmed) {
            eventPublisher.publishEvent(new AppointmentEvent(appointment.getId(), AppointmentEventType.CANCELLED));
        }
    }

    private void applyReschedule(Appointment appointment, Instant newScheduledAt, UUID groupKey) {
        appointment.setScheduledAt(newScheduledAt);
        appointment.setGroupKey(groupKey);
        appointment.setReminderSent(false); // El recordatorio 24h se recalcula para la nueva fecha
        appointmentRepository.save(appointment);
    }

    private void validateReschedulable(Appointment appointment) {
        if (appointment.getServicePlan().getModality() == Modality.RECORDED) {
            throw new BusinessRuleException("Recorded content can't be rescheduled");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Only pending or confirmed appointments can be rescheduled. Current status: " + appointment.getStatus());
        }
        if (!appointment.getScheduledAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("Past appointments can't be rescheduled");
        }
    }

    private void validateCancellable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Only pending or confirmed appointments can be cancelled. Current status: " + appointment.getStatus());
        }
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED
                && !appointment.getScheduledAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("Past appointments can't be cancelled");
        }
    }

    /**
     * Calcula el monto a cobrar (incluye IVA) según la moneda elegida.
     */
    private BigDecimal calculateBookingAmount(ServicePlan servicePlan, Currency currency) {
        BigDecimal basePrice = currency == Currency.COP
                ? servicePlan.getPriceCop()
                : servicePlan.getPriceUsd();

        return basePrice
                .multiply(BigDecimal.ONE.add(servicePlan.getTaxRate()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePenalty(BigDecimal paidAmount) {
        return paidAmount.multiply(schedulingProperties.getPenaltyRate()).setScale(2, RoundingMode.HALF_UP);
    }

    private Payment findCompletedBookingPayment(Appointment appointment) {
        return paymentRepository.findFirstByAppointmentIdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
                        appointment.getId(), PaymentType.BOOKING, PaymentStatus.COMPLETED)
                .orElseThrow(() -> new BusinessRuleException("The appointment has no completed booking payment"));
    }

    private Currency latestPaymentCurrency(Appointment appointment) {
        Payment latest = latestPaymentOf(appointment);
        return latest != null ? latest.getCurrency() : Currency.COP;
    }

    private Instant paymentDeadline() {
        return Instant.now().plus(Duration.ofHours(schedulingProperties.getPaymentDeadlineHours()));
    }

    private Appointment findOwned(UUID userId, UUID appointmentId) {
        return appointmentRepository.findByIdAndUserId(appointmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException());
    }

    private Appointment findOrThrow(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException());
    }

    private Payment latestPaymentOf(Appointment appointment) {
        return paymentRepository.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId())
                .stream()
                .findFirst()
                .orElse(null);
    }

}
