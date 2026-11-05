package com.joselorza.app.application.service;

import com.joselorza.app.application.event.AppointmentEvent;
import com.joselorza.app.application.event.AppointmentEventType;
import com.joselorza.app.application.event.PaymentEvent;
import com.joselorza.app.application.event.PaymentEventType;
import com.joselorza.app.application.port.out.PaymentGatewayPort;
import com.joselorza.app.application.port.out.PaymentInitiationRequest;
import com.joselorza.app.application.port.out.PaymentInitiationResult;
import com.joselorza.app.application.port.out.PaymentWebhookResult;
import com.joselorza.app.application.port.out.ReceiptData;
import com.joselorza.app.application.port.out.ReceiptGeneratorPort;
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
import com.joselorza.app.infrastructure.adapter.payment.PaymentSandboxProperties;
import com.joselorza.app.infrastructure.config.AppProperties;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.presentation.dto.request.RefundRequest;
import com.joselorza.app.presentation.dto.response.PaymentResponse;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.presentation.mapper.PaymentMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentGatewayResolver paymentGatewayResolver;
    private final AppointmentSlotValidator slotValidator;
    private final ReceiptGeneratorPort receiptGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final SchedulingProperties schedulingProperties;
    private final AppProperties appProperties;
    private final PaymentSandboxProperties sandboxProperties;
    private final PaymentMapper paymentMapper;

    /** Archivo PDF del comprobante listo para descargar. */
    public record ReceiptFile(String fileName, byte[] content) {
    }

    /** Resultado simulado de la pasarela (solo con el simulador de desarrollo). */
    public enum SandboxResult {
        APPROVED, DECLINED, PENDING
    }

    /**
     * Detalle de un pago. Un usuario solo ve los suyos; el admin, cualquiera.
     * El frontend lo consulta al volver de la pasarela mientras espera el webhook.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, UUID requesterId, boolean isAdmin) {
        return paymentMapper.toResponse(findVisiblePayment(paymentId, requesterId, isAdmin));
    }

    /**
     * Simula la respuesta de la pasarela para un pago (solo desarrollo).
     * Aplica exactamente la misma lógica que un webhook real.
     */
    @Transactional
    public PaymentResponse applySandboxResult(UUID paymentId, UUID requesterId, SandboxResult result) {
        if (!sandboxProperties.isEnabled()) {
            throw new ResourceNotFoundException();
        }

        Payment payment = findVisiblePayment(paymentId, requesterId, false);
        payment = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();

        if (payment.getStatus() == PaymentStatus.PENDING) {
            if (result == SandboxResult.APPROVED) {
                completePayment(payment);
            } else if (result == SandboxResult.DECLINED) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        }

        return paymentMapper.toResponse(payment);
    }

    /**
     * Crea un intento de pago y lo inicia en la pasarela elegida (MANUAL si es null).
     * El Payment se persiste primero para usar su ID como referencia ante la pasarela.
     */
    @Transactional
    public Payment createPayment(Appointment appointment, PaymentType paymentType, BigDecimal amount,
                                 Currency currency, PaymentGateway gateway) {
        PaymentGateway selectedGateway = gateway == null ? PaymentGateway.MANUAL : gateway;
        PaymentGatewayPort port = paymentGatewayResolver.resolve(selectedGateway);
        User user = appointment.getUser();

        if (selectedGateway == PaymentGateway.WOMPI && currency != Currency.COP) {
            throw new BusinessRuleException("Wompi only supports payments in COP");
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .user(user)
                .appointment(appointment)
                .gateway(selectedGateway)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .paymentType(paymentType)
                .build());

        String description = (paymentType == PaymentType.PENALTY ? "Penalización - " : "Reserva - ")
                + appointment.getServicePlan().getName();

        // En desarrollo, el simulador reemplaza a la pasarela real (nunca en producción)
        PaymentInitiationResult result = usesSandbox(selectedGateway)
                ? new PaymentInitiationResult(null, PaymentStatus.PENDING, null,
                        sandboxProperties.getCheckoutUrl() + "?paymentId=" + payment.getId())
                : port.initiatePayment(new PaymentInitiationRequest(
                        payment.getId(), appointment.getId(), amount, currency, paymentType,
                        user.getEmail(), user.getName(), description));

        payment.setGatewayTransactionId(result.gatewayTransactionId());
        payment.setStatus(result.status());
        payment.setRawResponse(result.rawResponse());
        payment.setCheckoutUrl(result.checkoutUrl());

        return paymentRepository.save(payment);
    }

    /**
     * Marca como FAILED todos los intentos pendientes de una cita
     * (al reintentar con otra pasarela, al cancelar o al expirar el plazo).
     */
    @Transactional
    public void failPendingPayments(UUID appointmentId) {
        paymentRepository.findByAppointmentIdAndStatus(appointmentId, PaymentStatus.PENDING)
                .forEach(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                });
    }

    /**
     * Confirma manualmente un pago (gateway MANUAL) y aplica la transición
     * de estado correspondiente sobre la cita asociada.
     */
    @Transactional
    public PaymentResponse confirmManualPayment(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        if (payment.getGateway() != PaymentGateway.MANUAL) {
            throw new BusinessRuleException(
                    "Only manual payments can be confirmed through this endpoint.");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException(
                    "The payment has already been processed previously. Current status: " + payment.getStatus());
        }

        completePayment(payment);

        return paymentMapper.toResponse(payment);
    }

    /**
     * Procesa el webhook de una pasarela: valida firma (en el adaptador), concilia
     * con el Payment interno y confirma la cita automáticamente si el pago fue aprobado.
     * Es idempotente: reenvíos del mismo evento no generan efectos duplicados.
     */
    @Transactional
    public void handleWebhook(PaymentGateway gateway, String payload, Map<String, String> headers) {
        PaymentWebhookResult result = paymentGatewayResolver.resolve(gateway).processWebhook(payload, headers);

        if (result.paymentId() == null) {
            log.info("{} webhook without a recognizable reference, ignored", gateway);
            return;
        }

        Payment payment = paymentRepository.findByIdForUpdate(result.paymentId()).orElse(null);
        if (payment == null || payment.getGateway() != gateway) {
            log.warn("{} webhook for unknown payment {}", gateway, result.paymentId());
            return;
        }

        payment.setRawResponse(result.rawPayload());
        if (payment.getGatewayTransactionId() == null && result.gatewayTransactionId() != null) {
            payment.setGatewayTransactionId(result.gatewayTransactionId());
        }

        switch (result.status()) {
            case COMPLETED -> {
                if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUNDED) {
                    paymentRepository.save(payment); // Reenvío del mismo evento
                    return;
                }
                if (!amountMatches(payment, result)) {
                    log.error("Amount/currency mismatch in {} webhook for payment {}: expected {} {}, received {} {}",
                            gateway, payment.getId(), payment.getAmount(), payment.getCurrency(),
                            result.amount(), result.currency());
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    return;
                }
                completePayment(payment);
            }
            case FAILED -> {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.FAILED);
                }
                paymentRepository.save(payment);
            }
            default -> paymentRepository.save(payment);
        }
    }

    /**
     * Marca un pago como reembolsado (registro interno). La devolución del dinero
     * se gestiona fuera de la plataforma: Wompi y Global66 no tienen API de reembolso.
     */
    @Transactional
    public PaymentResponse refund(UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Only completed payments can be refunded. Current status: " + payment.getStatus());
        }

        if (payment.getPaymentType() != PaymentType.BOOKING) {
            throw new BusinessRuleException("Only booking payments can be refunded");
        }

        if (payment.getAppointment().getStatus() != AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Only payments of cancelled appointments can be refunded");
        }

        BigDecimal refundAmount = request.amount() != null
                ? request.amount().setScale(2, RoundingMode.HALF_UP)
                : calculateRefundAmount(payment);

        if (refundAmount.signum() < 0 || refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BusinessRuleException("The refund amount must be between 0 and the paid amount");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAmount(refundAmount);
        payment.setRefundReason(request.reason());
        payment.setRefundedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        eventPublisher.publishEvent(new PaymentEvent(saved.getId(), PaymentEventType.REFUNDED));

        return paymentMapper.toResponse(saved);
    }

    /**
     * Monto sugerido de reembolso: lo pagado menos la penalización por cancelación tardía (si aplica).
     */
    public BigDecimal calculateRefundAmount(Payment payment) {
        Appointment appointment = payment.getAppointment();
        BigDecimal penalty = Boolean.TRUE.equals(appointment.getPenaltyApplied()) && appointment.getPenaltyAmount() != null
                ? appointment.getPenaltyAmount()
                : BigDecimal.ZERO;
        return payment.getAmount().subtract(penalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(UUID userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    /**
     * Listado administrativo con filtros opcionales (fechas en zona horaria del proveedor).
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> findAll(LocalDate from, LocalDate to, PaymentStatus status,
                                         PaymentGateway gateway, UUID userId) {
        return findEntities(DateRange.of(from, to, schedulingProperties.providerZone()), status, gateway, userId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Payment> findEntities(DateRange range, PaymentStatus status, PaymentGateway gateway, UUID userId) {
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (range.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
            }
            if (range.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), range.to()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (gateway != null) {
                predicates.add(cb.equal(root.get("gateway"), gateway));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return paymentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Genera el comprobante PDF de un pago COMPLETED o REFUNDED.
     * Un usuario solo puede descargar sus propios comprobantes; el admin, cualquiera.
     */
    @Transactional(readOnly = true)
    public ReceiptFile generateReceipt(UUID paymentId, UUID requesterId, boolean isAdmin) {
        Payment payment = findVisiblePayment(paymentId, requesterId, isAdmin);

        if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new BusinessRuleException("The receipt is only available for completed payments");
        }

        User user = payment.getUser();
        Appointment appointment = payment.getAppointment();
        ServicePlan servicePlan = appointment.getServicePlan();
        ZoneId zone = ZoneId.of(user.getTimezone());

        // El monto almacenado incluye IVA: base = total / (1 + tasa)
        BigDecimal taxRate = servicePlan.getTaxRate();
        BigDecimal total = payment.getAmount();
        BigDecimal base = total.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
        BigDecimal tax = total.subtract(base);

        AppProperties.Receipt issuer = appProperties.getReceipt();
        Instant paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getUpdatedAt();

        ReceiptData data = new ReceiptData(
                payment.getId().toString(),
                payment.getGatewayTransactionId() != null ? payment.getGatewayTransactionId() : payment.getId().toString(),
                Instant.now().atZone(zone),
                paidAt.atZone(zone),
                issuer.getIssuerName(),
                issuer.getIssuerId(),
                issuer.getIssuerEmail(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                servicePlan.getName(),
                payment.getPaymentType() == PaymentType.PENALTY ? "Penalización por reagendamiento" : "Reserva de servicio",
                servicePlan.getModality() == Modality.RECORDED ? null : appointment.getScheduledAt().atZone(zone),
                payment.getGateway().name(),
                payment.getCurrency().name(),
                base,
                taxRate,
                tax,
                total,
                payment.getStatus().name(),
                payment.getRefundedAmount()
        );

        return new ReceiptFile("comprobante-" + payment.getId() + ".pdf", receiptGenerator.generate(data));
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private Payment findVisiblePayment(UUID paymentId, UUID requesterId, boolean isAdmin) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        if (!isAdmin && !payment.getUser().getId().equals(requesterId)) {
            throw new ResourceNotFoundException(); // No se revela la existencia de pagos ajenos
        }
        return payment;
    }

    private boolean usesSandbox(PaymentGateway gateway) {
        return sandboxProperties.isEnabled() && gateway != PaymentGateway.MANUAL;
    }

    private void completePayment(Payment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());

        Appointment appointment = payment.getAppointment();
        AppointmentEventType appointmentEvent = applyAppointmentTransition(appointment, payment.getPaymentType());

        appointmentRepository.save(appointment);
        paymentRepository.save(payment);

        eventPublisher.publishEvent(new PaymentEvent(payment.getId(), PaymentEventType.COMPLETED));
        if (appointmentEvent != null) {
            eventPublisher.publishEvent(new AppointmentEvent(appointment.getId(), appointmentEvent));
        }
    }

    /**
     * Aplica la transición de la cita tras un pago exitoso y retorna el evento a publicar (o null).
     * Solo actúa si la cita sigue esperando ese pago (evita efectos de webhooks tardíos).
     */
    private AppointmentEventType applyAppointmentTransition(Appointment appointment, PaymentType paymentType) {
        if (paymentType == PaymentType.BOOKING) {
            if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
                log.warn("Booking payment completed for appointment {} in status {}: requires manual review",
                        appointment.getId(), appointment.getStatus());
                return null;
            }

            appointment.setPaymentDueAt(null);

            if (appointment.getServicePlan().getModality() == Modality.RECORDED) {
                appointment.setStatus(AppointmentStatus.COMPLETED);
                return null;
            }

            appointment.setStatus(AppointmentStatus.CONFIRMED);
            return AppointmentEventType.CONFIRMED;
        }

        if (paymentType == PaymentType.PENALTY) {
            if (appointment.getPendingRescheduledAt() == null || appointment.getStatus() != AppointmentStatus.CONFIRMED) {
                // Las cancelaciones tardías no generan pago: la penalidad se descuenta del reembolso
                log.warn("Penalty payment completed for appointment {} without a pending reschedule: requires manual review",
                        appointment.getId());
                return null;
            }

            // Reprogramación tardía: se aplica la nueva fecha (el horario quedó reservado al solicitarla)
            Instant newScheduledAt = appointment.getPendingRescheduledAt();
            UUID groupKey = appointment.getGroupKey();
            try {
                groupKey = slotValidator.validateCapacityAndResolveGroupKey(
                        appointment.getServicePlan(), newScheduledAt, appointment.getId());
            } catch (BusinessRuleException e) {
                log.error("The reserved slot for appointment {} is no longer consistent: {}",
                        appointment.getId(), e.getMessage());
            }

            appointment.setScheduledAt(newScheduledAt);
            appointment.setGroupKey(groupKey);
            appointment.setPendingRescheduledAt(null);
            appointment.setPaymentDueAt(null);
            appointment.setReminderSent(false);
            appointment.setStatus(AppointmentStatus.CONFIRMED);
            return AppointmentEventType.RESCHEDULED;
        }

        return null;
    }

    private boolean amountMatches(Payment payment, PaymentWebhookResult result) {
        boolean amountOk = result.amount() != null && result.amount().compareTo(payment.getAmount()) == 0;
        boolean currencyOk = result.currency() == null
                || result.currency().equalsIgnoreCase(payment.getCurrency().name());
        return amountOk && currencyOk;
    }
}
