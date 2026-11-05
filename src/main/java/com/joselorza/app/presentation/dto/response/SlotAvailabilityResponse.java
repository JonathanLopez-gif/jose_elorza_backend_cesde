package com.joselorza.app.presentation.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SlotAvailabilityResponse(
        UUID serviceId,
        LocalDate date,
        String timezone,
        List<SlotResponse> slots
) {}
