package com.joselorza.app.application.service;

import com.joselorza.app.application.event.AppointmentEvent;
import com.joselorza.app.application.event.AppointmentEventType;
import com.joselorza.app.application.event.PaymentEvent;
import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.application.port.out.PaymentWebhookResult;
import com.joselorza.app.application.port.out.ReceiptGeneratorPort;
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
import com.joselorza.app.infrastructure.adapter.payment.PaymentSandboxProperties;
import com.joselorza.app.infrastructure.config.AppProperties;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.presentation.dto.request.RefundRequest;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private AppointmentSlotValidator slotValidator;
    @Mock private ReceiptGeneratorPort receiptGenerator;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentGatewayPort wompiPort;
    private PaymentService service;
    private Appointment appointment;
    private Payment payment;

    @BeforeEach
    void setUp() {
        wompiPort = mock(PaymentGatewayPort.class);
        when(wompiPort.getGatewayType()).thenReturn(PaymentGateway.WOMPI);

        service = new PaymentService(paymentRepository, appointmentRepository,
                new PaymentGatewayResolver(List.of(wompiPort)), slotValidator, receiptGenerator,
                eventPublisher, new SchedulingProperties(), new AppProperties(), new PaymentSandboxProperties(),
                new PaymentMapper());

        User user = User.builder().id(UUID.randomUUID()).name("Ana").email("ana@test.com")
                .role(Role.USER).timezone("America/Bogota").build();
        ServicePlan plan = ServicePlan.builder().id(UUID.randomUUID()).name("Mentoría")
                .modality(Modality.LIVE).sessionType(SessionType.INDIVIDUAL)
                .taxRate(new BigDecimal("0.19")).durationMinutes(60).build();
        appointment = Appointment.builder().id(UUID.randomUUID()).user(user).servicePlan(plan)
                .scheduledAt(Instant.now().plus(10, ChronoUnit.DAYS)).durationMinutes(60)
                .userTimezone("America/Bogota").status(AppointmentStatus.PENDING_PAYMENT)
                .paymentDueAt(Instant.now().plus(48, ChronoUnit.HOURS)).build();
        payment = Payment.builder().id(UUID.randomUUID()).user(user).appointment(appointment)
                .gateway(PaymentGateway.WOMPI).amount(new BigDecimal("119000.00")).currency(Currency.COP)
                .status(PaymentStatus.PENDING).paymentType(PaymentType.BOOKING).build();

        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approvedWebhook_completesPaymentAndConfirmsAppointment() {
        mockWebhook(PaymentStatus.COMPLETED, new BigDecimal("119000.00"));

        service.handleWebhook(PaymentGateway.WOMPI, "{}", Map.of());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getGatewayTransactionId()).isEqualTo("tx-1");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.getPaymentDueAt()).isNull();

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).hasAtLeastOneElementOfType(PaymentEvent.class);
        assertThat(events.getAllValues()).anySatisfy(event -> assertThat(event)
                .isEqualTo(new AppointmentEvent(appointment.getId(), AppointmentEventType.CONFIRMED)));
    }

    @Test
    void approvedWebhook_isIdempotent() {
        payment.setStatus(PaymentStatus.COMPLETED);
        mockWebhook(PaymentStatus.COMPLETED, new BigDecimal("119000.00"));

        service.handleWebhook(PaymentGateway.WOMPI, "{}", Map.of());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void approvedWebhook_withDifferentAmount_marksPaymentAsFailed() {
        mockWebhook(PaymentStatus.COMPLETED, new BigDecimal("1000.00"));

        service.handleWebhook(PaymentGateway.WOMPI, "{}", Map.of());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void declinedWebhook_marksPaymentAsFailed() {
        mockWebhook(PaymentStatus.FAILED, new BigDecimal("119000.00"));

        service.handleWebhook(PaymentGateway.WOMPI, "{}", Map.of());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
    }

    @Test
    void refund_deductsLateCancellationPenalty() {
        payment.setStatus(PaymentStatus.COMPLETED);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setPenaltyApplied(true);
        appointment.setPenaltyAmount(new BigDecimal("11900.00"));

        PaymentResponse response = service.refund(payment.getId(), new RefundRequest(null, "Cancelación tardía"));

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.refundedAmount()).isEqualByComparingTo("107100.00");
        assertThat(response.refundedAt()).isNotNull();
    }

    @Test
    void refund_ofActiveAppointment_isRejected() {
        payment.setStatus(PaymentStatus.COMPLETED);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        assertThatThrownBy(() -> service.refund(payment.getId(), new RefundRequest(null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    private void mockWebhook(PaymentStatus status, BigDecimal amount) {
        when(wompiPort.processWebhook(any(), any())).thenReturn(
                new PaymentWebhookResult(payment.getId(), "tx-1", status, amount, "COP", "{}"));
    }
}
