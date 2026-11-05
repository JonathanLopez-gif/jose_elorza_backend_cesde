package com.joselorza.app.presentation.dto.response;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        String timezone,
        boolean available,
        List<AvailabilityBlockResponse> blocks
) {}
