package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.enumerated.AppointmentStatus;
import com.joselorza.app.domain.model.enumerated.Currency;
import com.joselorza.app.domain.model.enumerated.PaymentStatus;
import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.PaymentRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.presentation.dto.response.AppointmentResponse;
import com.joselorza.app.presentation.dto.response.DashboardResponse;
import com.joselorza.app.presentation.dto.response.MonthlyPointResponse;
import com.joselorza.app.presentation.dto.response.RevenueResponse;
import com.joselorza.app.presentation.dto.response.StatusCountResponse;
import com.joselorza.app.presentation.mapper.AppointmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Métricas del panel administrativo: citas del día, ingresos del mes y tendencias.
 * "Hoy" y "este mes" se calculan en la zona horaria del proveedor.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<AppointmentStatus> DAY_STATUSES = List.of(
            AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CONFIRMED,
            AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

    // Citas "reales" (pagadas): se excluyen canceladas y pendientes de pago
    private static final List<AppointmentStatus> BOOKED_STATUSES = List.of(
            AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

    private static final int MONTHLY_SERIES_SIZE = 6;

    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AppointmentService appointmentService;
    private final SchedulingProperties schedulingProperties;
    private final AppointmentMapper appointmentMapper;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        ZoneId zone = schedulingProperties.providerZone();
        LocalDate today = LocalDate.now(zone);

        DateRange day = DateRange.of(today, today, zone);
        Instant dayEndInclusive = day.to().minusMillis(1);

        long appointmentsToday = appointmentRepository
                .countByScheduledAtBetweenAndStatusIn(day.from(), dayEndInclusive, DAY_STATUSES);
        long confirmedToday = appointmentRepository
                .countByScheduledAtBetweenAndStatusIn(day.from(), dayEndInclusive, List.of(AppointmentStatus.CONFIRMED));

        List<AppointmentResponse> todayAppointments = appointmentService.findEntities(day, null, null).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .map(a -> appointmentMapper.toResponse(a, null))
                .toList();

        YearMonth currentMonth = YearMonth.from(today);
        DateRange month = monthRange(currentMonth, zone);

        List<RevenueResponse> monthRevenue = Arrays.stream(Currency.values())
                .map(currency -> revenueOf(currency, month))
                .toList();

        long appointmentsMonth = appointmentRepository.countByScheduledAtBetweenAndStatusIn(
                month.from(), month.to().minusMillis(1), BOOKED_STATUSES);

        List<AppointmentResponse> upcoming = appointmentRepository
                .findTop6ByStatusAndScheduledAtAfterOrderByScheduledAtAsc(AppointmentStatus.CONFIRMED, Instant.now())
                .stream()
                .map(a -> appointmentMapper.toResponse(a, null))
                .toList();

        return new DashboardResponse(
                today,
                zone.getId(),
                appointmentsToday,
                confirmedToday,
                appointmentsMonth,
                appointmentRepository.countByStatus(AppointmentStatus.PENDING_PAYMENT),
                paymentRepository.countByStatus(PaymentStatus.PENDING),
                userRepository.countByRoleAndIsActiveTrue(Role.USER),
                monthRevenue,
                monthlySeries(currentMonth, zone),
                countByStatus(),
                todayAppointments,
                upcoming
        );
    }

    private List<MonthlyPointResponse> monthlySeries(YearMonth currentMonth, ZoneId zone) {
        List<MonthlyPointResponse> series = new ArrayList<>();
        for (int i = MONTHLY_SERIES_SIZE - 1; i >= 0; i--) {
            YearMonth yearMonth = currentMonth.minusMonths(i);
            DateRange range = monthRange(yearMonth, zone);
            series.add(new MonthlyPointResponse(
                    yearMonth.toString(),
                    revenueOf(Currency.COP, range).net(),
                    revenueOf(Currency.USD, range).net(),
                    appointmentRepository.countByScheduledAtBetweenAndStatusIn(
                            range.from(), range.to().minusMillis(1), BOOKED_STATUSES)));
        }
        return series;
    }

    private List<StatusCountResponse> countByStatus() {
        return appointmentRepository.countGroupedByStatus().stream()
                .map(row -> new StatusCountResponse((AppointmentStatus) row[0], (Long) row[1]))
                .toList();
    }

    private DateRange monthRange(YearMonth yearMonth, ZoneId zone) {
        return DateRange.of(yearMonth.atDay(1), yearMonth.atEndOfMonth(), zone);
    }

    private RevenueResponse revenueOf(Currency currency, DateRange range) {
        // Ingreso bruto: todo lo cobrado en el mes (incluye pagos que luego se reembolsaron)
        BigDecimal gross = paymentRepository.sumPaidAmount(
                List.of(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED), currency, range.from(), range.to());
        BigDecimal refunded = paymentRepository.sumRefundedAmount(
                PaymentStatus.REFUNDED, currency, range.from(), range.to());
        return new RevenueResponse(currency, gross, refunded, gross.subtract(refunded));
    }
}
