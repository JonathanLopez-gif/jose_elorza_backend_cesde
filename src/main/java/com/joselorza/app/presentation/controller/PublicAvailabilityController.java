package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AvailabilityQueryService;
import com.joselorza.app.presentation.dto.response.AvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class PublicAvailabilityController {

    private final AvailabilityQueryService availabilityQueryService;

    @GetMapping
    public AvailabilityResponse getAvailability(
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return availabilityQueryService.getAvailability(date);
    }
}