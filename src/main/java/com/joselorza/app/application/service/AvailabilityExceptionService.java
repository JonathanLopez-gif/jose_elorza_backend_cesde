package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.AvailabilityException;
import com.joselorza.app.infrastructure.repository.AvailabilityExceptionRepository;
import com.joselorza.app.presentation.dto.request.AvailabilityExceptionRequest;
import com.joselorza.app.presentation.dto.response.AvailabilityExceptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityExceptionService {

    private final AvailabilityExceptionRepository repository;

    @Transactional
    public AvailabilityExceptionResponse create(AvailabilityExceptionRequest request) {
        if (repository.existsByDate(request.date())) {
            throw new BusinessRuleException(
                    "There is already a availability exception for the date " + request.date());
        }

        AvailabilityException exception = AvailabilityException.builder()
                .date(request.date())
                .reason(request.reason())
                .isAvailable(false) // fase actual: solo bloqueo
                .build();

        return toResponse(repository.save(exception));
    }

    @Transactional
    public AvailabilityExceptionResponse update(UUID id, AvailabilityExceptionRequest request) {
        AvailabilityException exception = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException());

        repository.findByDate(request.date())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "There is already another availability exception for the date " + request.date());
                });

        exception.setDate(request.date());
        exception.setReason(request.reason());
        // isAvailable se mantiene false; no se actualiza desde el request en esta fase

        return toResponse(repository.save(exception));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityExceptionResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AvailabilityExceptionResponse findById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException());
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException();
        }
        repository.deleteById(id); // hard delete, consistente con AvailabilityRule
    }

    private AvailabilityExceptionResponse toResponse(AvailabilityException exception) {
        return new AvailabilityExceptionResponse(
                exception.getId(),
                exception.getDate(),
                exception.getIsAvailable(),
                exception.getReason(),
                exception.getCreatedAt(),
                exception.getUpdatedAt()
        );
    }
}