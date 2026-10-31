package com.joselorza.app.presentation.dto.response;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        boolean available,
        List<AvailabilityBlockResponse> blocks
) {}