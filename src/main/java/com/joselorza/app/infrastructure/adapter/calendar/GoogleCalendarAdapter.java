package com.joselorza.app.infrastructure.adapter.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.joselorza.app.application.port.out.CalendarEventData;
import com.joselorza.app.application.port.out.CalendarPort;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ExternalServiceException;
import com.joselorza.app.domain.model.GoogleCalendarCredential;
import com.joselorza.app.infrastructure.repository.GoogleCalendarCredentialRepository;
import com.joselorza.app.infrastructure.security.TokenEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de Google Calendar API v3 usando OAuth2 con autorización persistente
 * del administrador (token único del sistema). El refresh token se guarda cifrado
 * con AES-GCM y el access token se mantiene solo en memoria.
 */
@Slf4j
@Component
public class GoogleCalendarAdapter implements CalendarPort {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events";
    private static final String EVENT_URL = EVENTS_URL + "/{eventId}";
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";

    private final GoogleCalendarProperties properties;
    private final GoogleCalendarCredentialRepository credentialRepository;
    private final RestClient restClient;

    private TokenEncryptor encryptor;
    private String cachedAccessToken;
    private Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

    public GoogleCalendarAdapter(GoogleCalendarProperties properties,
                                 GoogleCalendarCredentialRepository credentialRepository,
                                 RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.credentialRepository = credentialRepository;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isEnabled() {
        return properties.isConfigured();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isConnected() {
        return isEnabled() && credentialRepository.findFirstByOrderByUpdatedAtDesc().isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Instant connectedAt() {
        return credentialRepository.findFirstByOrderByUpdatedAtDesc()
                .map(GoogleCalendarCredential::getUpdatedAt)
                .orElse(null);
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        ensureEnabled();
        return UriComponentsBuilder.fromHttpUrl(AUTH_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")   // Necesario para obtener refresh token
                .queryParam("prompt", "consent")        // Fuerza la emisión del refresh token
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    @Override
    @Transactional
    public void connect(String authorizationCode, UUID adminId) {
        ensureEnabled();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", "authorization_code");

        JsonNode response = postToken(form);
        String refreshToken = response.path("refresh_token").asText(null);
        if (refreshToken == null) {
            throw new ExternalServiceException("Google did not return a refresh token");
        }

        credentialRepository.deleteAll(); // Token único del sistema
        credentialRepository.save(GoogleCalendarCredential.builder()
                .encryptedRefreshToken(encryptor().encrypt(refreshToken))
                .calendarId(properties.getCalendarId())
                .connectedBy(adminId)
                .build());

        cacheAccessToken(response);
        log.info("Google Calendar connected by admin {}", adminId);
    }

    @Override
    @Transactional
    public void disconnect() {
        credentialRepository.findFirstByOrderByUpdatedAtDesc().ifPresent(credential -> {
            try {
                restClient.post()
                        .uri(REVOKE_URL + "?token={token}", encryptor().decrypt(credential.getEncryptedRefreshToken()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException e) {
                log.warn("Could not revoke the Google token (it will be deleted anyway): {}", e.getMessage());
            }
        });
        credentialRepository.deleteAll();
        synchronized (this) {
            cachedAccessToken = null;
            cachedAccessTokenExpiresAt = Instant.EPOCH;
        }
    }

    @Override
    public String createEvent(CalendarEventData event) {
        try {
            JsonNode response = restClient.post()
                    .uri(EVENTS_URL, calendarId())
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGoogleEvent(event))
                    .retrieve()
                    .body(JsonNode.class);
            return response == null ? null : response.path("id").asText(null);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Could not create the Google Calendar event", e);
        }
    }

    @Override
    public void updateEvent(String eventId, CalendarEventData event) {
        try {
            restClient.patch()
                    .uri(EVENT_URL, calendarId(), eventId)
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toGoogleEvent(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ExternalServiceException("Could not update the Google Calendar event", e);
        }
    }

    @Override
    public void deleteEvent(String eventId) {
        try {
            restClient.delete()
                    .uri(EVENT_URL, calendarId(), eventId)
                    .header("Authorization", "Bearer " + accessToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // 404/410: el evento ya no existe en el calendario, no es un error
            if (e.getStatusCode() != HttpStatus.NOT_FOUND && e.getStatusCode() != HttpStatus.GONE) {
                throw new ExternalServiceException("Could not delete the Google Calendar event", e);
            }
        } catch (RestClientException e) {
            throw new ExternalServiceException("Could not delete the Google Calendar event", e);
        }
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private Map<String, Object> toGoogleEvent(CalendarEventData event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", event.summary());
        body.put("description", event.description());
        if (event.location() != null) {
            body.put("location", event.location());
        }
        body.put("start", Map.of("dateTime", event.start().toString(), "timeZone", "UTC"));
        body.put("end", Map.of("dateTime", event.end().toString(), "timeZone", "UTC"));
        return body;
    }

    private synchronized String accessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(cachedAccessTokenExpiresAt)) {
            return cachedAccessToken;
        }

        GoogleCalendarCredential credential = credentialRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new BusinessRuleException("Google Calendar is not connected"));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("refresh_token", encryptor().decrypt(credential.getEncryptedRefreshToken()));
        form.add("grant_type", "refresh_token");

        cacheAccessToken(postToken(form));
        return cachedAccessToken;
    }

    private synchronized void cacheAccessToken(JsonNode tokenResponse) {
        cachedAccessToken = tokenResponse.path("access_token").asText(null);
        long expiresIn = tokenResponse.path("expires_in").asLong(3600);
        // Margen de 60s para no usar un token a punto de expirar
        cachedAccessTokenExpiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - 60));
    }

    private JsonNode postToken(MultiValueMap<String, String> form) {
        try {
            JsonNode response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("access_token")) {
                throw new ExternalServiceException("Invalid response from Google OAuth2");
            }
            return response;
        } catch (RestClientException e) {
            throw new ExternalServiceException("Error communicating with Google OAuth2", e);
        }
    }

    private String calendarId() {
        return credentialRepository.findFirstByOrderByUpdatedAtDesc()
                .map(GoogleCalendarCredential::getCalendarId)
                .orElse(properties.getCalendarId());
    }

    private synchronized TokenEncryptor encryptor() {
        if (encryptor == null) {
            encryptor = new TokenEncryptor(properties.getEncryptionKey());
        }
        return encryptor;
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new BusinessRuleException("The Google Calendar integration is not enabled");
        }
    }
}
