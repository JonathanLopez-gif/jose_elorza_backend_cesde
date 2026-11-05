package com.joselorza.app.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record MonthAvailabilityResponse(
        UUID serviceId,
        String month,
        String timezone,
        List<DayAvailabilityResponse> days
) {}
