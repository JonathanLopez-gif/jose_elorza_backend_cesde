package com.joselorza.app.presentation.dto.response;

import java.time.Instant;

public record GoogleCalendarStatusResponse(
        boolean enabled,
        boolean connected,
        Instant connectedAt,
        String calendarId
) {
}
