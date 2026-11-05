package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exportación CSV de citas y pagos por rango de fechas (zona horaria del proveedor).
 * Se incluye BOM UTF-8 para que Excel muestre correctamente tildes y eñes.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String BOM = "﻿";
    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppointmentService appointmentService;
    private final PaymentService paymentService;
    private final SchedulingProperties schedulingProperties;

    @Transactional(readOnly = true)
    public byte[] exportAppointments(LocalDate from, LocalDate to) {
        ZoneId zone = schedulingProperties.providerZone();
        List<Appointment> appointments = appointmentService.findEntities(DateRange.required(from, to, zone), null, null);

        StringBuilder csv = new StringBuilder(BOM);
        csv.append(row("id", "scheduled_at_utc", "scheduled_at_local", "user_name", "user_email", "service",
                "modality", "duration_minutes", "status", "penalty_applied", "penalty_amount", "meeting_link",
                "cancelled_at_utc", "cancellation_reason", "created_at_utc"));

        for (Appointment a : appointments) {
            csv.append(row(
                    a.getId().toString(),
                    utc(a.getScheduledAt()),
                    local(a.getScheduledAt(), zone),
                    a.getUser().getName(),
                    a.getUser().getEmail(),
                    a.getServicePlan().getName(),
                    a.getServicePlan().getModality().name(),
                    String.valueOf(a.getDurationMinutes()),
                    a.getStatus().name(),
                    String.valueOf(a.getPenaltyApplied()),
                    a.getPenaltyAmount() == null ? "" : a.getPenaltyAmount().toPlainString(),
                    a.getMeetingLink(),
                    utc(a.getCancelledAt()),
                    a.getCancellationReason(),
                    utc(a.getCreatedAt())));
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportPayments(LocalDate from, LocalDate to) {
        ZoneId zone = schedulingProperties.providerZone();
        List<Payment> payments = paymentService.findEntities(DateRange.required(from, to, zone), null, null, null);

        StringBuilder csv = new StringBuilder(BOM);
        csv.append(row("id", "created_at_utc", "paid_at_utc", "user_name", "user_email", "service",
                "appointment_id", "payment_type", "gateway", "gateway_transaction_id", "amount", "currency",
                "status", "refunded_amount", "refunded_at_utc", "refund_reason"));

        for (Payment p : payments) {
            csv.append(row(
                    p.getId().toString(),
                    utc(p.getCreatedAt()),
                    utc(p.getPaidAt()),
                    p.getUser().getName(),
                    p.getUser().getEmail(),
                    p.getAppointment().getServicePlan().getName(),
                    p.getAppointment().getId().toString(),
                    p.getPaymentType().name(),
                    p.getGateway().name(),
                    p.getGatewayTransactionId(),
                    p.getAmount().toPlainString(),
                    p.getCurrency().name(),
                    p.getStatus().name(),
                    p.getRefundedAmount() == null ? "" : p.getRefundedAmount().toPlainString(),
                    utc(p.getRefundedAt()),
                    p.getRefundReason()));
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private String row(String... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(values[i]));
        }
        return line.append("\r\n").toString();
    }

    /**
     * Escapa según RFC 4180 y neutraliza fórmulas (CSV injection) en celdas
     * que comienzan con =, +, - o @.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0 && !isNumeric(safe)) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private boolean isNumeric(String value) {
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    private String utc(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String local(Instant instant, ZoneId zone) {
        return instant == null ? "" : instant.atZone(zone).format(LOCAL_FORMAT);
    }
}
