package com.joselorza.app.application.service;

import com.joselorza.app.application.event.AppointmentEventType;
import com.joselorza.app.application.port.out.CalendarEventData;
import com.joselorza.app.application.port.out.CalendarPort;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.infrastructure.adapter.calendar.GoogleCalendarProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.presentation.dto.response.GoogleCalendarAuthorizationResponse;
import com.joselorza.app.presentation.dto.response.GoogleCalendarStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta la integración con Google Calendar:
 * - Flujo OAuth2 del administrador (una sola vez, token único del sistema).
 * - Creación / actualización / eliminación de eventos al confirmar, reagendar o cancelar citas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final CalendarPort calendarPort;
    private final AppointmentRepository appointmentRepository;
    private final GoogleCalendarProperties properties;

    // state OAuth2 -> (admin, expiración). Protege el callback contra CSRF; un solo uso.
    private final Map<String, PendingAuthorization> pendingStates = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    private record PendingAuthorization(UUID adminId, Instant expiresAt) {
    }

    public GoogleCalendarStatusResponse getStatus() {
        return new GoogleCalendarStatusResponse(
                calendarPort.isEnabled(),
                calendarPort.isConnected(),
                calendarPort.connectedAt(),
                properties.getCalendarId());
    }

    public GoogleCalendarAuthorizationResponse startAuthorization(UUID adminId) {
        if (!calendarPort.isEnabled()) {
            throw new BusinessRuleException("The Google Calendar integration is not enabled");
        }

        purgeExpiredStates();

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        pendingStates.put(state, new PendingAuthorization(adminId, Instant.now().plus(STATE_TTL)));

        return new GoogleCalendarAuthorizationResponse(calendarPort.buildAuthorizationUrl(state));
    }

    /**
     * Procesa el callback de Google. Retorna la URL del panel a la que redirigir al navegador.
     */
    public String completeAuthorization(String code, String state, String error) {
        String redirect = properties.getPostAuthorizationRedirect();

        PendingAuthorization pending = state == null ? null : pendingStates.remove(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            log.warn("Google OAuth2 callback with invalid or expired state");
            return redirect + "?google_calendar=error&message=" + encode("The authorization link expired, try again");
        }

        if (error != null || code == null) {
            log.warn("Google OAuth2 authorization denied: {}", error);
            return redirect + "?google_calendar=cancelled";
        }

        try {
            calendarPort.connect(code, pending.adminId());
            return redirect + "?google_calendar=success";
        } catch (Exception e) {
            log.error("Error completing the Google OAuth2 flow", e);
            return redirect + "?google_calendar=error";
        }
    }

    public void disconnect() {
        calendarPort.disconnect();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Sincroniza el evento del calendario del administrador con el estado actual de la cita.
     * Si la integración no está conectada, no hace nada.
     */
    @Transactional
    public void syncAppointment(UUID appointmentId, AppointmentEventType eventType) {
        if (!calendarPort.isConnected()) {
            return;
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException());

        if (eventType == AppointmentEventType.CANCELLED || appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            if (appointment.getGoogleEventId() != null) {
                calendarPort.deleteEvent(appointment.getGoogleEventId());
                appointment.setGoogleEventId(null);
                appointmentRepository.save(appointment);
            }
            return;
        }

        CalendarEventData event = toEventData(appointment);

        if (appointment.getGoogleEventId() == null) {
            appointment.setGoogleEventId(calendarPort.createEvent(event));
            appointmentRepository.save(appointment);
        } else {
            calendarPort.updateEvent(appointment.getGoogleEventId(), event);
        }
    }

    private CalendarEventData toEventData(Appointment appointment) {
        String summary = appointment.getServicePlan().getName() + " - " + appointment.getUser().getName();

        StringBuilder description = new StringBuilder()
                .append("Cliente: ").append(appointment.getUser().getName()).append('\n')
                .append("Correo: ").append(appointment.getUser().getEmail()).append('\n');
        if (appointment.getUser().getPhone() != null) {
            description.append("Teléfono: ").append(appointment.getUser().getPhone()).append('\n');
        }
        description.append("Modalidad: ").append(appointment.getServicePlan().getModality()).append('\n')
                .append("Zona horaria del cliente: ").append(appointment.getUserTimezone()).append('\n')
                .append("ID de la cita: ").append(appointment.getId());
        if (appointment.getMeetingLink() != null) {
            description.append('\n').append("Enlace: ").append(appointment.getMeetingLink());
        }

        return new CalendarEventData(
                summary,
                description.toString(),
                appointment.getScheduledAt(),
                appointment.getScheduledAt().plus(Duration.ofMinutes(appointment.getDurationMinutes())),
                appointment.getMeetingLink());
    }

    private void purgeExpiredStates() {
        Instant now = Instant.now();
        pendingStates.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
