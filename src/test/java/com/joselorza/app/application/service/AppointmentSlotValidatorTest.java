package com.joselorza.app.application.service;

import com.joselorza.app.application.service.AppointmentSlotValidator.Commitment;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.SessionType;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentSlotValidatorTest {

    private static final Instant TEN = Instant.parse("2026-12-01T15:00:00Z");

    private final AppointmentSlotValidator validator =
            new AppointmentSlotValidator(null, null, null, new SchedulingProperties());

    private final ServicePlan individual = plan(SessionType.INDIVIDUAL, 60, null);
    private final ServicePlan group = plan(SessionType.GROUP, 90, 3);

    @Test
    void freeSlot_hasFullCapacity() {
        assertThat(validator.remainingCapacity(individual, TEN, List.of())).isEqualTo(1);
        assertThat(validator.remainingCapacity(group, TEN, List.of())).isEqualTo(3);
    }

    @Test
    void overlappingByDuration_isUnavailable_evenWithDifferentStart() {
        // Cita de 90 min a las 10:00: una nueva a las 11:00 se cruza aunque no empiecen igual
        List<Commitment> busy = List.of(commitment(group, TEN, 90));

        assertThat(validator.remainingCapacity(individual, TEN.plusSeconds(3600), busy)).isZero();
        // Termina justo cuando empieza la siguiente: no hay choque
        assertThat(validator.remainingCapacity(individual, TEN.plusSeconds(90 * 60), busy)).isEqualTo(1);
        // Empieza antes y termina después del inicio de la existente
        assertThat(validator.remainingCapacity(individual, TEN.minusSeconds(30 * 60), busy)).isZero();
    }

    @Test
    void groupSession_sharesExactSlotUntilCapacityIsReached() {
        List<Commitment> two = List.of(commitment(group, TEN, 90), commitment(group, TEN, 90));
        assertThat(validator.remainingCapacity(group, TEN, two)).isEqualTo(1);

        List<Commitment> three = List.of(commitment(group, TEN, 90), commitment(group, TEN, 90), commitment(group, TEN, 90));
        assertThat(validator.remainingCapacity(group, TEN, three)).isZero();

        // Mismo servicio grupal pero otro horario que se cruza: no se puede compartir
        assertThat(validator.remainingCapacity(group, TEN.plusSeconds(1800), two)).isZero();
    }

    @Test
    void individualSession_isNeverShared() {
        List<Commitment> busy = List.of(commitment(individual, TEN, 60));
        assertThat(validator.remainingCapacity(individual, TEN, busy)).isZero();
    }

    private ServicePlan plan(SessionType type, int minutes, Integer capacity) {
        return ServicePlan.builder().id(UUID.randomUUID()).name("Plan").durationMinutes(minutes)
                .modality(Modality.LIVE).sessionType(type).maxCapacity(capacity).build();
    }

    private Commitment commitment(ServicePlan plan, Instant start, int minutes) {
        return new Commitment(UUID.randomUUID(), plan.getId(), start, start.plusSeconds(minutes * 60L), null);
    }
}
