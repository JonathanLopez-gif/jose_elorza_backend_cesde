package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Rango de fechas [from, to) en instantes UTC, calculado a partir de fechas
 * locales (inclusive) interpretadas en una zona horaria. Ambos extremos son opcionales.
 */
public record DateRange(Instant from, Instant to) {

    public static DateRange of(LocalDate fromDate, LocalDate toDate, ZoneId zone) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessRuleException("The start date must be before or equal to the end date");
        }
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        return new DateRange(from, to);
    }

    /** Variante que exige ambos extremos (ej. exportaciones CSV). */
    public static DateRange required(LocalDate fromDate, LocalDate toDate, ZoneId zone) {
        if (fromDate == null || toDate == null) {
            throw new BusinessRuleException("The date range (from, to) is required");
        }
        return of(fromDate, toDate, zone);
    }
}
