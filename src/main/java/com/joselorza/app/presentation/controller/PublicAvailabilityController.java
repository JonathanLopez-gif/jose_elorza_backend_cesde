package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AvailabilityQueryService;
import com.joselorza.app.presentation.dto.response.AvailabilityResponse;
import com.joselorza.app.presentation.dto.response.MonthAvailabilityResponse;
import com.joselorza.app.presentation.dto.response.SlotAvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class PublicAvailabilityController {

    private final AvailabilityQueryService availabilityQueryService;

    /**
     * Disponibilidad de un día. timezone (IANA, ej. America/Mexico_City) es opcional:
     * si se envía, la fecha y los bloques se expresan en esa zona horaria;
     * si no, se usa la zona horaria del proveedor.
     */
    @GetMapping
    public AvailabilityResponse getAvailability(
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(required = false) String timezone) {
        return availabilityQueryService.getAvailability(date, timezone);
    }

    /**
     * Horarios reservables de un servicio en un día (zona horaria del usuario).
     */
    @GetMapping("/slots")
    public SlotAvailabilityResponse getSlots(
            @RequestParam UUID servicePlanId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(required = false) String timezone) {
        return availabilityQueryService.getSlots(servicePlanId, date, timezone);
    }

    /**
     * Resumen mensual (AAAA-MM) para pintar el calendario.
     */
    @GetMapping("/month")
    public MonthAvailabilityResponse getMonth(
            @RequestParam UUID servicePlanId,
            @RequestParam String month,
            @RequestParam(required = false) String timezone) {
        return availabilityQueryService.getMonthAvailability(servicePlanId, month, timezone);
    }
}
