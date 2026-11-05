package com.joselorza.app.application.event;

import com.joselorza.app.application.service.GoogleCalendarService;
import com.joselorza.app.application.service.NotificationService;
import com.joselorza.app.domain.model.enumerated.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Procesa los efectos secundarios de los eventos de dominio DESPUÉS del commit
 * y de forma asíncrona: un fallo de SMTP o de Google nunca revierte una cita o un pago.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private final NotificationService notificationService;
    private final GoogleCalendarService googleCalendarService;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onAppointmentEvent(AppointmentEvent event) {
        try {
            googleCalendarService.syncAppointment(event.appointmentId(), event.type());
        } catch (Exception e) {
            log.error("Error syncing appointment {} with Google Calendar", event.appointmentId(), e);
        }

        NotificationType notificationType = switch (event.type()) {
            case CONFIRMED -> NotificationType.BOOKING_CONFIRMED;
            case RESCHEDULED -> NotificationType.RESCHEDULE;
            case CANCELLED -> NotificationType.CANCELLATION;
            case MEETING_LINK_UPDATED -> null; // Solo se actualiza el calendario
        };

        if (notificationType != null) {
            try {
                notificationService.notifyAppointment(event.appointmentId(), notificationType);
            } catch (Exception e) {
                log.error("Error notifying appointment {} ({})", event.appointmentId(), notificationType, e);
            }
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPaymentEvent(PaymentEvent event) {
        NotificationType notificationType = switch (event.type()) {
            case COMPLETED -> NotificationType.PAYMENT_SUCCESS;
            case REFUNDED -> NotificationType.REFUND;
        };

        try {
            notificationService.notifyPayment(event.paymentId(), notificationType);
        } catch (Exception e) {
            log.error("Error notifying payment {} ({})", event.paymentId(), notificationType, e);
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            notificationService.sendPasswordReset(event.userId(), event.rawToken());
        } catch (Exception e) {
            log.error("Error sending password reset email to user {}", event.userId(), e);
        }
    }
}
