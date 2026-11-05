package com.joselorza.app.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joselorza.app.application.port.out.EmailSenderPort;
import com.joselorza.app.application.service.EmailTemplateBuilder.EmailContent;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Notification;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.domain.model.enumerated.NotificationChannel;
import com.joselorza.app.domain.model.enumerated.NotificationStatus;
import com.joselorza.app.domain.model.enumerated.NotificationType;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.NotificationRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.infrastructure.security.JwtProperties;
import com.joselorza.app.presentation.dto.response.NotificationResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Envía notificaciones por Email y registra cada intento en la tabla notifications.
 * Las notificaciones de agenda respetan la preferencia notifyEmail del usuario;
 * las transaccionales (pago, reembolso, recuperación de contraseña) se envían siempre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<NotificationType> PREFERENCE_BASED_TYPES = EnumSet.of(
            NotificationType.BOOKING_CONFIRMED,
            NotificationType.CANCELLATION,
            NotificationType.RESCHEDULE,
            NotificationType.REMINDER);

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final EmailSenderPort emailSender;
    private final EmailTemplateBuilder templateBuilder;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    /**
     * Notifica un cambio en una cita. Retorna true si el correo se envió.
     */
    @Transactional
    public boolean notifyAppointment(UUID appointmentId, NotificationType type) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        EmailContent content = switch (type) {
            case BOOKING_CONFIRMED -> templateBuilder.bookingConfirmed(appointment);
            case RESCHEDULE -> templateBuilder.rescheduled(appointment);
            case CANCELLATION -> templateBuilder.cancelled(appointment);
            case REMINDER -> templateBuilder.reminder(appointment);
            default -> throw new IllegalArgumentException("Unsupported appointment notification: " + type);
        };

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", appointmentId);
        payload.put("scheduledAt", appointment.getScheduledAt());
        payload.put("meetingLink", appointment.getMeetingLink());

        return send(appointment.getUser(), type, content, payload);
    }

    @Transactional
    public boolean notifyPayment(UUID paymentId, NotificationType type) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        EmailContent content = switch (type) {
            case PAYMENT_SUCCESS -> templateBuilder.paymentSuccess(payment);
            case REFUND -> templateBuilder.refund(payment);
            default -> throw new IllegalArgumentException("Unsupported payment notification: " + type);
        };

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("appointmentId", payment.getAppointment().getId());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("refundedAmount", payment.getRefundedAmount());

        return send(payment.getUser(), type, content, payload);
    }

    @Transactional
    public boolean sendPasswordReset(UUID userId, String rawToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException());

        EmailContent content = templateBuilder.passwordReset(
                user, rawToken, jwtProperties.getPasswordResetExpirationMinutes());

        // El token nunca se guarda en el payload del log
        return send(user, NotificationType.PASSWORD_RESET, content, new LinkedHashMap<>());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findAll(UUID userId, NotificationType type, NotificationStatus status) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return notificationRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private boolean send(User user, NotificationType type, EmailContent content, Map<String, Object> payload) {
        if (PREFERENCE_BASED_TYPES.contains(type) && !user.isNotifyEmail()) {
            log.debug("User {} has email notifications disabled, {} skipped", user.getId(), type);
            return false;
        }

        payload.put("to", user.getEmail());
        payload.put("subject", content.subject());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .payload(toJson(payload))
                .build());

        try {
            emailSender.send(user.getEmail(), content.subject(), content.html());
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Error sending {} email to {}", type, user.getEmail(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        notificationRepository.save(notification);
        return notification.getStatus() == NotificationStatus.SENT;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUser().getId(),
                notification.getUser().getEmail(),
                notification.getType(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getPayload(),
                notification.getErrorMessage(),
                notification.getSentAt(),
                notification.getCreatedAt()
        );
    }
}
