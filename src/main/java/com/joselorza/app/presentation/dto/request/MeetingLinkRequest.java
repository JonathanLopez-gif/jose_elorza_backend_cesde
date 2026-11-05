package com.joselorza.app.presentation.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Asignación del enlace de videoconferencia (Meet/Teams). null o vacío lo elimina.
 */
public record MeetingLinkRequest(

        @Size(max = 500, message = "The meeting link can't exceed 500 characters")
        @Pattern(regexp = "^$|^https://\\S+$", message = "The meeting link must be a valid https URL")
        String meetingLink

) {
}
