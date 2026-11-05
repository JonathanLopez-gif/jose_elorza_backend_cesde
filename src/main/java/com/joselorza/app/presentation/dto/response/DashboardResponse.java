package com.joselorza.app.presentation.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Métricas del panel administrativo.
 */
public record DashboardResponse(
        LocalDate date,
        String timezone,
        long appointmentsToday,
        long confirmedToday,
        long appointmentsMonth,
        long appointmentsPendingPayment,
        long paymentsPending,
        long activeUsers,
        List<RevenueResponse> monthRevenue,
        List<MonthlyPointResponse> monthly,      // Últimos 6 meses (incluye el actual)
        List<StatusCountResponse> byStatus,
        List<AppointmentResponse> todayAppointments,
        List<AppointmentResponse> upcoming       // Próximas citas confirmadas
) {
}
