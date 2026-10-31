package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.AvailabilityRule;
import com.joselorza.app.infrastructure.repository.AvailabilityExceptionRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityRuleRepository;
import com.joselorza.app.presentation.dto.response.AvailabilityBlockResponse;
import com.joselorza.app.presentation.dto.response.AvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Servicio de solo lectura que combina AvailabilityRule (reglas semanales
 * recurrentes) y AvailabilityException (bloqueos puntuales) para responder
 * si el proveedor tiene disponibilidad en una fecha específica.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityQueryService {

    private final AvailabilityRuleRepository ruleRepository;
    private final AvailabilityExceptionRepository exceptionRepository;

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(LocalDate date) {

        // 1. Verificar si existe una excepción que bloquee la fecha completa.
        boolean blockedByException = exceptionRepository.findByDate(date)
                .map(exception -> !Boolean.TRUE.equals(exception.getIsAvailable()))
                .orElse(false);

        if (blockedByException) {
            return new AvailabilityResponse(date, false, Collections.emptyList());
        }

        // 2. Si no hay excepción, consultar las reglas semanales del día correspondiente.
        // Convención ISO-8601: 1 = lunes ... 7 = domingo.
        int dayOfWeek = date.getDayOfWeek().getValue();

        List<AvailabilityRule> rules =
                ruleRepository.findByDayOfWeekAndIsAvailableTrueOrderByStartTimeAsc(dayOfWeek);

        List<AvailabilityBlockResponse> blocks = rules.stream()
                .map(rule -> new AvailabilityBlockResponse(rule.getStartTime(), rule.getEndTime()))
                .toList();

        return new AvailabilityResponse(date, !blocks.isEmpty(), blocks);
    }
}