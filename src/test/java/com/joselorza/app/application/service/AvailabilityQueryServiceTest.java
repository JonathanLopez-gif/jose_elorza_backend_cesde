package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.AvailabilityException;
import com.joselorza.app.domain.model.AvailabilityRule;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.repository.AvailabilityExceptionRepository;
import com.joselorza.app.infrastructure.repository.AvailabilityRuleRepository;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.presentation.dto.response.AvailabilityBlockResponse;
import com.joselorza.app.presentation.dto.response.AvailabilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityQueryServiceTest {

    // 2026-11-09 es lunes (dayOfWeek = 1)
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Mock private AvailabilityRuleRepository ruleRepository;
    @Mock private AvailabilityExceptionRepository exceptionRepository;
    @Mock private ServicePlanRepository servicePlanRepository;
    @Mock private AppointmentSlotValidator slotValidator;

    private AvailabilityQueryService service;

    @BeforeEach
    void setUp() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setProviderTimezone("America/Bogota"); // UTC-5, sin horario de verano

        service = new AvailabilityQueryService(ruleRepository, exceptionRepository, servicePlanRepository,
                slotValidator, properties);

        when(exceptionRepository.findByDate(any())).thenReturn(Optional.empty());
        when(ruleRepository.findByDayOfWeekAndIsAvailableTrueOrderByStartTimeAsc(1)).thenReturn(List.of(
                AvailabilityRule.builder()
                        .dayOfWeek(1)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(12, 0))
                        .isAvailable(true)
                        .build()));
    }

    @Test
    void withoutTimezone_usesProviderTimezone() {
        AvailabilityResponse response = service.getAvailability(MONDAY, null);

        assertThat(response.timezone()).isEqualTo("America/Bogota");
        assertThat(response.blocks()).singleElement().satisfies(block -> {
            assertThat(block.startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(block.endTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(block.startAt()).isEqualTo(Instant.parse("2026-11-09T14:00:00Z"));
        });
    }

    @Test
    void withUserTimezone_convertsBlocksToUserLocalTime() {
        // Madrid en noviembre: UTC+1 → 09:00 Bogotá = 15:00 Madrid
        AvailabilityResponse response = service.getAvailability(MONDAY, "Europe/Madrid");

        AvailabilityBlockResponse block = response.blocks().get(0);
        assertThat(block.startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(block.endTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(block.startAt()).isEqualTo(Instant.parse("2026-11-09T14:00:00Z"));
    }

    @Test
    void whenBlockCrossesUserMidnight_itIsClippedToTheRequestedDay() {
        // Tokio (UTC+9): el bloque lunes 14:00-17:00 UTC es 23:00 lunes - 02:00 martes en Tokio
        AvailabilityResponse response = service.getAvailability(MONDAY, "Asia/Tokyo");

        assertThat(response.available()).isTrue();
        AvailabilityBlockResponse block = response.blocks().get(0);
        assertThat(block.startTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(block.endAt()).isEqualTo(Instant.parse("2026-11-09T15:00:00Z"));

        // El resto del bloque aparece el martes en Tokio
        AvailabilityResponse tuesday = service.getAvailability(MONDAY.plusDays(1), "Asia/Tokyo");
        assertThat(tuesday.blocks()).singleElement().satisfies(b -> {
            assertThat(b.startTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(b.endTime()).isEqualTo(LocalTime.of(2, 0));
        });
    }

    @Test
    void blockedDate_returnsNotAvailable() {
        when(exceptionRepository.findByDate(MONDAY)).thenReturn(Optional.of(
                AvailabilityException.builder().date(MONDAY).isAvailable(false).reason("Festivo").build()));

        AvailabilityResponse response = service.getAvailability(MONDAY, null);

        assertThat(response.available()).isFalse();
        assertThat(response.blocks()).isEmpty();
    }
}
