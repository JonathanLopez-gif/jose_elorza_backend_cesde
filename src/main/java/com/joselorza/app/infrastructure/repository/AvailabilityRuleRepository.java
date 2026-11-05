package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    /** Usado para validar solapamientos al crear/actualizar reglas del mismo día. */
    List<AvailabilityRule> findByDayOfWeek(Integer dayOfWeek);

    /** Usado por el endpoint público de disponibilidad. */
    List<AvailabilityRule> findByIsAvailableTrueOrderByDayOfWeekAscStartTimeAsc();

    /** Usado por el endpoint público de disponibilidad. */
    List<AvailabilityRule> findByDayOfWeekAndIsAvailableTrueOrderByStartTimeAsc(Integer dayOfWeek);
}