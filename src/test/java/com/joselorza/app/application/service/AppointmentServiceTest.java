package com.joselorza.app.application.service;

import com.joselorza.app.application.event.AppointmentEvent;
import com.joselorza.app.application.event.AppointmentEventType;
import com.joselorza.app.domain.exception.BusinessRuleException;
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
import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.domain.model.enumerated.SessionType;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.presentation.dto.request.CancelAppointmentRequest;
import com.joselorza.app.presentation.dto.request.RescheduleRequest;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import com.joselorza.app.presentation.mapper.AppointmentMapper;
import com.joselorza.app.presentation.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ServicePlanRepository servicePlanRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppointmentSlotValidator slotValidator;
    @Mock private PaymentService paymentService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AppointmentService service;
    private User user;
    private ServicePlan servicePlan;

    @BeforeEach
    void setUp() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setPenaltyRate(new BigDecimal("0.10"));
        properties.setPaymentDeadlineHours(48);

        service = new AppointmentService(appointmentRepository, paymentRepository, servicePlanRepository,
                userRepository, slotValidator, paymentService, properties, eventPublisher,
                new AppointmentMapper(new PaymentMapper()));

        user = User.builder().id(UUID.randomUUID()).name("Ana").email("ana@test.com")
                .role(Role.USER).timezone("America/Bogota").build();
        servicePlan = ServicePlan.builder().id(UUID.randomUUID()).name("Mentoría")
                .durationMinutes(60).priceCop(new BigDecimal("100000")).priceUsd(new BigDecimal("25"))
                .modality(Modality.LIVE).sessionType(SessionType.INDIVIDUAL)
                .taxRate(new BigDecimal("0.19")).isActive(true).build();

        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByAppointmentIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
    }

    @Test
    void reschedule_withLessThan7Days_createsPenaltyPaymentAndKeepsCurrentDate() {
        Instant currentDate = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant newDate = Instant.now().plus(10, ChronoUnit.DAYS);
        Appointment appointment = confirmedAppointment(currentDate);
        Payment booking = completedBooking(appointment, new BigDecimal("119000.00"));

        when(appointmentRepository.findByIdAndUserId(appointment.getId(), user.getId())).thenReturn(Optional.of(appointment));
        when(slotValidator.isWithinPenaltyWindow(currentDate)).thenReturn(true);
        when(paymentRepository.findFirstByAppointmentIdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
                appointment.getId(), PaymentType.BOOKING, PaymentStatus.COMPLETED)).thenReturn(Optional.of(booking));
        when(paymentService.createPayment(any(), eq(PaymentType.PENALTY), any(), any(), any()))
                .thenAnswer(inv -> Payment.builder().id(UUID.randomUUID()).user(user).appointment(appointment)
                        .amount(inv.getArgument(2)).currency(inv.getArgument(3)).gateway(inv.getArgument(4))
                        .paymentType(PaymentType.PENALTY).status(PaymentStatus.PENDING).build());

        AppointmentResponse response = service.reschedule(user.getId(), appointment.getId(),
                new RescheduleRequest(newDate, null));

        // La fecha original se mantiene hasta pagar la penalidad del 10%
        assertThat(response.scheduledAt()).isEqualTo(currentDate);
        assertThat(response.pendingRescheduledAt()).isEqualTo(newDate);
        assertThat(response.penaltyApplied()).isTrue();
        assertThat(response.penaltyAmount()).isEqualByComparingTo("11900.00");
        assertThat(response.paymentDueAt()).isAfter(Instant.now().plus(Duration.ofHours(47)));
        assertThat(response.latestPayment().paymentType()).isEqualTo(PaymentType.PENALTY);
        assertThat(response.latestPayment().gateway()).isEqualTo(PaymentGateway.WOMPI); // misma pasarela del pago original
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reschedule_with7DaysOrMore_movesDateKeepingOriginalPayment() {
        Instant currentDate = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant newDate = Instant.now().plus(12, ChronoUnit.DAYS);
        Appointment appointment = confirmedAppointment(currentDate);
        appointment.setReminderSent(true);

        when(appointmentRepository.findByIdAndUserId(appointment.getId(), user.getId())).thenReturn(Optional.of(appointment));
        when(slotValidator.isWithinPenaltyWindow(currentDate)).thenReturn(false);

        AppointmentResponse response = service.reschedule(user.getId(), appointment.getId(),
                new RescheduleRequest(newDate, null));

        assertThat(response.scheduledAt()).isEqualTo(newDate);
        assertThat(response.penaltyApplied()).isFalse();
        assertThat(appointment.getReminderSent()).isFalse();
        verify(paymentService, never()).createPayment(any(), any(), any(), any(), any());

        ArgumentCaptor<AppointmentEvent> event = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(AppointmentEventType.RESCHEDULED);
    }

    @Test
    void reschedule_recordedService_isRejected() {
        servicePlan.setModality(Modality.RECORDED);
        Appointment appointment = confirmedAppointment(Instant.now().plus(10, ChronoUnit.DAYS));
        when(appointmentRepository.findByIdAndUserId(appointment.getId(), user.getId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.reschedule(user.getId(), appointment.getId(),
                new RescheduleRequest(Instant.now().plus(12, ChronoUnit.DAYS), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancel_withLessThan7Days_registersPenaltyOf10Percent() {
        Instant date = Instant.now().plus(2, ChronoUnit.DAYS);
        Appointment appointment = confirmedAppointment(date);
        Payment booking = completedBooking(appointment, new BigDecimal("119000.00"));

        when(appointmentRepository.findByIdAndUserId(appointment.getId(), user.getId())).thenReturn(Optional.of(appointment));
        when(slotValidator.isWithinPenaltyWindow(date)).thenReturn(true);
        when(paymentRepository.findFirstByAppointmentIdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
                appointment.getId(), PaymentType.BOOKING, PaymentStatus.COMPLETED)).thenReturn(Optional.of(booking));

        AppointmentResponse response = service.cancel(user.getId(), appointment.getId(),
                new CancelAppointmentRequest("Viaje"));

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.penaltyApplied()).isTrue();
        assertThat(response.penaltyAmount()).isEqualByComparingTo("11900.00");
        assertThat(response.cancellationReason()).isEqualTo("Viaje");
        assertThat(response.cancelledAt()).isNotNull();
        verify(paymentService).failPendingPayments(appointment.getId());
    }

    @Test
    void cancel_with7DaysOrMore_hasNoPenalty() {
        Instant date = Instant.now().plus(15, ChronoUnit.DAYS);
        Appointment appointment = confirmedAppointment(date);

        when(appointmentRepository.findByIdAndUserId(appointment.getId(), user.getId())).thenReturn(Optional.of(appointment));
        when(slotValidator.isWithinPenaltyWindow(date)).thenReturn(false);

        AppointmentResponse response = service.cancel(user.getId(), appointment.getId(), null);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.penaltyApplied()).isFalse();
        assertThat(response.penaltyAmount()).isNull();
    }

    @Test
    void cancel_completedAppointment_isRejected() {
        Appointment appointment = confirmedAppointment(Instant.now().minus(1, ChronoUnit.DAYS));
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdAndUserId(appointment.getId(), user.getId())).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancel(user.getId(), appointment.getId(), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- helpers ---

    private Appointment confirmedAppointment(Instant scheduledAt) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .servicePlan(servicePlan)
                .scheduledAt(scheduledAt)
                .durationMinutes(60)
                .userTimezone("America/Bogota")
                .status(AppointmentStatus.CONFIRMED)
                .penaltyApplied(false)
                .reminderSent(false)
                .build();
    }

    private Payment completedBooking(Appointment appointment, BigDecimal amount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .appointment(appointment)
                .gateway(PaymentGateway.WOMPI)
                .amount(amount)
                .currency(Currency.COP)
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.BOOKING)
                .build();
    }
}
