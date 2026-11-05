package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.AvailabilityRule;
import com.joselorza.app.infrastructure.repository.AvailabilityRuleRepository;
import com.joselorza.app.presentation.dto.request.AvailabilityRuleRequest;
import com.joselorza.app.presentation.dto.response.AvailabilityRuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityRuleService {

    private final AvailabilityRuleRepository repository;

    @Transactional
    public AvailabilityRuleResponse create(AvailabilityRuleRequest request) {
        validateTimeRange(request.startTime(), request.endTime());

        boolean isAvailable = request.isAvailable() == null || request.isAvailable();

        if (isAvailable) {
            validateNoOverlap(request.dayOfWeek(), request.startTime(), request.endTime(), null);
        }

        AvailabilityRule rule = AvailabilityRule.builder()
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .isAvailable(isAvailable)
                .build();

        return toResponse(repository.save(rule));
    }

    @Transactional
    public AvailabilityRuleResponse update(UUID id, AvailabilityRuleRequest request) {
        AvailabilityRule rule = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException());

        validateTimeRange(request.startTime(), request.endTime());

        boolean isAvailable = request.isAvailable() == null || request.isAvailable();

        if (isAvailable) {
            validateNoOverlap(request.dayOfWeek(), request.startTime(), request.endTime(), id);
        }

        rule.setDayOfWeek(request.dayOfWeek());
        rule.setStartTime(request.startTime());
        rule.setEndTime(request.endTime());
        rule.setIsAvailable(isAvailable);

        return toResponse(repository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AvailabilityRuleResponse findById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException());
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException();
        }
        repository.deleteById(id); // eliminación física: no hay historial que preservar
    }

    // --- Validaciones privadas ---

    private void validateTimeRange(java.time.LocalTime start, java.time.LocalTime end) {
        if (!start.isBefore(end)) {
            throw new BusinessRuleException("The start time must be before the end time");
        }
    }

    private void validateNoOverlap(Integer dayOfWeek, java.time.LocalTime start, java.time.LocalTime end, UUID excludeId) {
        List<AvailabilityRule> existing = repository.findByDayOfWeek(dayOfWeek);

        boolean overlaps = existing.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsAvailable()))
                .filter(r -> excludeId == null || !r.getId().equals(excludeId))
                .anyMatch(r -> start.isBefore(r.getEndTime()) && r.getStartTime().isBefore(end));

        if (overlaps) {
            throw new BusinessRuleException(
                    "The block overlaps with another existing availability rule on the same day");
        }
    }

    private AvailabilityRuleResponse toResponse(AvailabilityRule rule) {
        return new AvailabilityRuleResponse(
                rule.getId(),
                rule.getDayOfWeek(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getIsAvailable(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}