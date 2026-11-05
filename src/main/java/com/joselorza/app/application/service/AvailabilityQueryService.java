package com.joselorza.app.application.service;

import com.joselorza.app.application.service.AppointmentSlotValidator.Commitment;
import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.AvailabilityRule;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AvailabilityExceptionRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityRuleRepository;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.presentation.dto.response.AvailabilityBlockResponse;
import com.joselorza.app.presentation.dto.response.AvailabilityResponse;
import com.joselorza.app.presentation.dto.response.DayAvailabilityResponse;
import com.joselorza.app.presentation.dto.response.MonthAvailabilityResponse;
import com.joselorza.app.presentation.dto.response.SlotAvailabilityResponse;
import com.joselorza.app.presentation.dto.response.SlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de solo lectura que combina AvailabilityRule (reglas semanales
 * recurrentes) y AvailabilityException (bloqueos puntuales) para responder
 * si el proveedor tiene disponibilidad en una fecha específica.
 *
 * Manejo de zonas horarias: las reglas se definen en la zona del proveedor.
 * La fecha consultada se interpreta en la zona del usuario; se calculan los
 * bloques del proveedor que se solapan con ese día y se devuelven tanto en
 * hora local del usuario como en instantes UTC (formato para reservar).
 */
@Service
@RequiredArgsConstructor
public class AvailabilityQueryService {

    private final AvailabilityRuleRepository ruleRepository;
    private final AvailabilityExceptionRepository exceptionRepository;
    private final ServicePlanRepository servicePlanRepository;
    private final AppointmentSlotValidator slotValidator;
    private final SchedulingProperties schedulingProperties;

    /** Bloque de disponibilidad del proveedor expresado en instantes UTC. */
    private record Block(Instant start, Instant end) {
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(LocalDate date, String timezone) {
        ZoneId userZone = resolveZone(timezone, schedulingProperties.providerZone());

        // Día completo solicitado, expresado en instantes UTC
        Instant dayStart = date.atStartOfDay(userZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(userZone).toInstant();

        List<AvailabilityBlockResponse> blocks = new ArrayList<>();

        for (Block block : providerBlocks(dayStart, dayEnd, new HashMap<>())) {
            // Recortar el bloque al día solicitado por el usuario.
            Instant start = block.start().isBefore(dayStart) ? dayStart : block.start();
            Instant end = block.end().isAfter(dayEnd) ? dayEnd : block.end();

            if (start.isBefore(end)) {
                blocks.add(new AvailabilityBlockResponse(
                        start.atZone(userZone).toLocalTime(),
                        end.atZone(userZone).toLocalTime(),
                        start,
                        end));
            }
        }

        blocks.sort(Comparator.comparing(AvailabilityBlockResponse::startAt));

        return new AvailabilityResponse(date, userZone.getId(), !blocks.isEmpty(), blocks);
    }

    /**
     * Horarios reservables de un servicio para un día (en la zona del usuario).
     * Cada bloque se divide en inicios cada N minutos donde la sesión completa cabe;
     * "available" indica si aún hay cupo (sin choques con otras citas y con anticipación suficiente).
     */
    @Transactional(readOnly = true)
    public SlotAvailabilityResponse getSlots(UUID servicePlanId, LocalDate date, String timezone) {
        ServicePlan servicePlan = findBookableService(servicePlanId);
        ZoneId userZone = resolveZone(timezone, schedulingProperties.providerZone());

        Instant dayStart = date.atStartOfDay(userZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(userZone).toInstant();
        List<Commitment> commitments = slotValidator.loadCommitments(dayStart, dayEnd.plus(Duration.ofDays(1)), false);

        List<SlotResponse> slots = computeSlots(servicePlan, dayStart, dayEnd, commitments, new HashMap<>());

        return new SlotAvailabilityResponse(servicePlan.getId(), date, userZone.getId(), slots);
    }

    /**
     * Resumen por día de un mes (AAAA-MM) para pintar el calendario: total de horarios y cuántos siguen libres.
     */
    @Transactional(readOnly = true)
    public MonthAvailabilityResponse getMonthAvailability(UUID servicePlanId, String month, String timezone) {
        ServicePlan servicePlan = findBookableService(servicePlanId);
        ZoneId userZone = resolveZone(timezone, schedulingProperties.providerZone());

        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessRuleException("The month must have the format YYYY-MM");
        }

        Instant monthStart = yearMonth.atDay(1).atStartOfDay(userZone).toInstant();
        Instant monthEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay(userZone).toInstant();
        // Una sola consulta de citas para todo el mes
        List<Commitment> commitments = slotValidator.loadCommitments(monthStart, monthEnd.plus(Duration.ofDays(1)), false);
        Map<Integer, List<AvailabilityRule>> rulesCache = new HashMap<>();

        List<DayAvailabilityResponse> days = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Instant dayStart = date.atStartOfDay(userZone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(userZone).toInstant();

            List<SlotResponse> slots = computeSlots(servicePlan, dayStart, dayEnd, commitments, rulesCache);
            int available = (int) slots.stream().filter(SlotResponse::available).count();
            days.add(new DayAvailabilityResponse(date, slots.size(), available));
        }

        return new MonthAvailabilityResponse(servicePlan.getId(), yearMonth.toString(), userZone.getId(), days);
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private List<SlotResponse> computeSlots(ServicePlan servicePlan, Instant dayStart, Instant dayEnd,
                                            List<Commitment> commitments,
                                            Map<Integer, List<AvailabilityRule>> rulesCache) {
        Duration duration = Duration.ofMinutes(servicePlan.getDurationMinutes());
        Duration step = Duration.ofMinutes(schedulingProperties.getSlotStepMinutes());
        Instant earliest = Instant.now().plus(Duration.ofHours(schedulingProperties.getMinNoticeHours()));

        List<SlotResponse> slots = new ArrayList<>();
        for (Block block : providerBlocks(dayStart, dayEnd, rulesCache)) {
            // Se recorre el bloque completo (sin recortar) y se conservan los inicios que caen en el día pedido
            for (Instant start = block.start();
                 !start.plus(duration).isAfter(block.end());
                 start = start.plus(step)) {

                if (start.isBefore(dayStart) || !start.isBefore(dayEnd) || start.isBefore(earliest)) {
                    continue;
                }

                boolean available = slotValidator.remainingCapacity(servicePlan, start, commitments) > 0;
                slots.add(new SlotResponse(start, start.plus(duration), available));
            }
        }

        slots.sort(Comparator.comparing(SlotResponse::startUtc));
        return slots;
    }

    /**
     * Bloques del proveedor (en UTC) de todos los días del proveedor que se solapan con [from, to).
     */
    private List<Block> providerBlocks(Instant from, Instant to, Map<Integer, List<AvailabilityRule>> rulesCache) {
        ZoneId providerZone = schedulingProperties.providerZone();

        // Días del proveedor que se solapan con el día del usuario (1 o 2 por diferencia horaria)
        LocalDate firstProviderDate = from.atZone(providerZone).toLocalDate();
        LocalDate lastProviderDate = to.minusNanos(1).atZone(providerZone).toLocalDate();

        List<Block> blocks = new ArrayList<>();

        for (LocalDate providerDate = firstProviderDate;
             !providerDate.isAfter(lastProviderDate);
             providerDate = providerDate.plusDays(1)) {

            // 1. Verificar si existe una excepción que bloquee la fecha completa.
            boolean blockedByException = exceptionRepository.findByDate(providerDate)
                    .map(exception -> !Boolean.TRUE.equals(exception.getIsAvailable()))
                    .orElse(false);

            if (blockedByException) {
                continue;
            }

            // 2. Consultar las reglas semanales del día correspondiente.
            // Convención ISO-8601: 1 = lunes ... 7 = domingo.
            int dayOfWeek = providerDate.getDayOfWeek().getValue();
            List<AvailabilityRule> rules = rulesCache.computeIfAbsent(dayOfWeek,
                    ruleRepository::findByDayOfWeekAndIsAvailableTrueOrderByStartTimeAsc);

            for (AvailabilityRule rule : rules) {
                Instant blockStart = ZonedDateTime.of(providerDate, rule.getStartTime(), providerZone).toInstant();
                Instant blockEnd = ZonedDateTime.of(providerDate, rule.getEndTime(), providerZone).toInstant();
                if (blockStart.isBefore(to) && blockEnd.isAfter(from)) {
                    blocks.add(new Block(blockStart, blockEnd));
                }
            }
        }
        return blocks;
    }

    private ServicePlan findBookableService(UUID servicePlanId) {
        ServicePlan servicePlan = servicePlanRepository.findById(servicePlanId)
                .filter(plan -> Boolean.TRUE.equals(plan.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException());

        if (servicePlan.getModality() == Modality.RECORDED) {
            throw new BusinessRuleException("Recorded content doesn't require choosing a schedule");
        }
        return servicePlan;
    }

    private ZoneId resolveZone(String timezone, ZoneId fallback) {
        if (timezone == null || timezone.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new BusinessRuleException("Invalid Timezone (it must be a valid IANA ID)");
        }
    }
}
