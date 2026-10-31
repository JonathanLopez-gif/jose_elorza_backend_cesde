package com.joselorza.app.application.service;

import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.application.port.out.PaymentInitiationRequest;
import com.joselorza.app.application.port.out.PaymentInitiationResult;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.AvailabilityException;
import com.joselorza.app.domain.model.AvailabilityRule;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.PaymentGateway;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import com.joselorza.app.domain.model.enumerated.SessionType;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityExceptionRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityRuleRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.presentation.dto.request.AppointmentRequest;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import com.joselorza.app.presentation.mapper.AppointmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CONFIRMED);

    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final ServicePlanRepository servicePlanRepository;
    private final UserRepository userRepository;
    private final AvailabilityRuleRepository availabilityRuleRepository;
    private final AvailabilityExceptionRepository availabilityExceptionRepository;
    private final PaymentGatewayResolver paymentGatewayResolver;
    private final AppointmentMapper appointmentMapper;

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

            validateAvailability(scheduledAt);
            groupKey = validateCapacityAndResolveGroupKey(servicePlan, scheduledAt);
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
                .paymentDueAt(Instant.now().plus(Duration.ofHours(48)))
                .build();

        appointment = appointmentRepository.save(appointment);

        Payment payment = createBookingPayment(user, appointment, servicePlan, request.currency());

        return appointmentMapper.toResponse(appointment, payment);
    }

    /**
     * Valida que la fecha/hora solicitada caiga dentro de un bloque de
     * disponibilidad activo y no esté bloqueada por una excepción puntual.
     * Se asume proveedor único operando en UTC.
     */
    private void validateAvailability(Instant scheduledAt) {
        LocalDate date = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalTime time = scheduledAt.atZone(ZoneOffset.UTC).toLocalTime();

        boolean isBlocked = availabilityExceptionRepository.findByDate(date)
                .map(AvailabilityException::getIsAvailable)
                .map(available -> !Boolean.TRUE.equals(available))
                .orElse(false);

        if (isBlocked) {
            throw new BusinessRuleException("The selected date is not available");
        }

        int dayOfWeek = date.getDayOfWeek().getValue(); // ISO: 1=lunes .. 7=domingo

        List<AvailabilityRule> rules =
                availabilityRuleRepository.findByDayOfWeekAndIsAvailableTrueOrderByStartTimeAsc(dayOfWeek);

        boolean withinBlock = rules.stream().anyMatch(rule ->
                !time.isBefore(rule.getStartTime()) && time.isBefore(rule.getEndTime()));

        if (!withinBlock) {
            throw new BusinessRuleException("The selected schedule is not available");
        }
    }

    /**
     * Verifica que el proveedor no tenga ningún compromiso activo en ese instante
     * (sin importar el servicio) y resuelve el groupKey a utilizar:
     * - Si no hay nada agendado en ese instante: se permite (GROUP genera groupKey nuevo).
     * - Si hay algo agendado pero de OTRO servicio: se rechaza (proveedor ocupado).
     * - Si hay algo agendado del MISMO servicio:
     *     - INDIVIDUAL: ya está ocupado, se rechaza.
     *     - GROUP: se valida capacidad y se reutiliza el groupKey existente.
     */
    private UUID validateCapacityAndResolveGroupKey(ServicePlan servicePlan, Instant scheduledAt) {
        List<Appointment> existingAtInstant =
                appointmentRepository.findActiveBookingsAtForUpdate(scheduledAt, ACTIVE_STATUSES);

        if (existingAtInstant.isEmpty()) {
            return servicePlan.getSessionType() == SessionType.GROUP
                    ? UUID.randomUUID()
                    : null;
        }

        boolean belongsToSameService = existingAtInstant.stream()
                .allMatch(a -> a.getServicePlan().getId().equals(servicePlan.getId()));

        if (!belongsToSameService) {
            throw new BusinessRuleException(
                    "The provider already has an appointment scheduled at that time");
        }

        if (servicePlan.getSessionType() == SessionType.INDIVIDUAL) {
            throw new BusinessRuleException("The selected time slot has already been booked");
        }

        int capacity = servicePlan.getMaxCapacity();
        if (existingAtInstant.size() >= capacity) {
            throw new BusinessRuleException("Maximum capacity reached for this time slot");
        }

        return existingAtInstant.stream()
                .map(Appointment::getGroupKey)
                .filter(key -> key != null)
                .findFirst()
                .orElse(UUID.randomUUID());
    }

    /**
     * Calcula el monto a cobrar (incluye IVA) según la moneda elegida
     * y crea el Payment inicial a través del adaptador MANUAL.
     */
    private Payment createBookingPayment(User user, Appointment appointment, ServicePlan servicePlan, Currency currency) {
        BigDecimal basePrice = currency == Currency.COP
                ? servicePlan.getPriceCop()
                : servicePlan.getPriceUsd();

        BigDecimal amount = basePrice
                .multiply(BigDecimal.ONE.add(servicePlan.getTaxRate()))
                .setScale(2, RoundingMode.HALF_UP);

        PaymentGatewayPort gateway = paymentGatewayResolver.resolve(PaymentGateway.MANUAL);

        PaymentInitiationResult result = gateway.initiatePayment(
                new PaymentInitiationRequest(appointment.getId(), amount, currency, PaymentType.BOOKING));

        Payment payment = Payment.builder()
                .user(user)
                .appointment(appointment)
                .gateway(PaymentGateway.MANUAL)
                .gatewayTransactionId(result.gatewayTransactionId())
                .amount(amount)
                .currency(currency)
                .status(result.status())
                .paymentType(PaymentType.BOOKING)
                .rawResponse(result.rawResponse())
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments(UUID userId) {
        return appointmentRepository.findByUserIdOrderByScheduledAtDesc(userId).stream()
                .map(appointment -> appointmentMapper.toResponse(appointment, latestPaymentOf(appointment)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(appointment -> appointmentMapper.toResponse(appointment, latestPaymentOf(appointment)))
                .toList();
    }

    private Payment latestPaymentOf(Appointment appointment) {
        return paymentRepository.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId())
                .stream()
                .findFirst()
                .orElse(null);
    }

}