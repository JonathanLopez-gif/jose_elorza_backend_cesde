package com.joselorza.app.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de salida para la integración con el calendario del administrador (Google Calendar).
 * La autorización es única para todo el sistema: los clientes nunca se autentican con Google.
 */
public interface CalendarPort {

    /** Indica si la integración está habilitada por configuración. */
    boolean isEnabled();

    /** Indica si el administrador ya completó el flujo OAuth2 (existe refresh token almacenado). */
    boolean isConnected();

    /** Momento en que se conectó/actualizó la autorización, o null si no está conectada. */
    Instant connectedAt();

    /** URL de consentimiento de Google a la que debe navegar el administrador. */
    String buildAuthorizationUrl(String state);

    /** Intercambia el código de autorización por tokens y persiste el refresh token cifrado. */
    void connect(String authorizationCode, UUID adminId);

    /** Revoca/elimina la autorización almacenada. */
    void disconnect();

    /** Crea un evento y retorna su ID externo. */
    String createEvent(CalendarEventData event);

    void updateEvent(String eventId, CalendarEventData event);

    void deleteEvent(String eventId);
}
