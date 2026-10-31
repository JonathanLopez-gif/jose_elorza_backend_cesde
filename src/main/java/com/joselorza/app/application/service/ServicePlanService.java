package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.enumerated.SessionType;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.presentation.dto.request.ServicePlanRequest;
import com.joselorza.app.presentation.dto.response.ServicePlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de aplicación para la gestión de planes de servicio (ServicePlan).
 * Contiene la lógica de negocio: validación de capacidad y mapeo DTO-Entidad.
 */
@Service
@RequiredArgsConstructor
public class ServicePlanService {

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.19");

    private final ServicePlanRepository servicePlanRepository;

    /**
     * Crea un nuevo plan de servicio.
     */
    @Transactional
    public ServicePlanResponse create(ServicePlanRequest request) {
        validateCapacity(request.sessionType(), request.maxCapacity());

        ServicePlan servicePlan = ServicePlan.builder()
                .name(request.name())
                .description(request.description())
                .durationMinutes(request.durationMinutes())
                .priceCop(request.priceCop())
                .priceUsd(request.priceUsd())
                .modality(request.modality())
                .sessionType(request.sessionType())
                .maxCapacity(request.maxCapacity())
                .taxRate(request.taxRate() != null ? request.taxRate() : DEFAULT_TAX_RATE)
                .isActive(true)
                .build();

        ServicePlan saved = servicePlanRepository.save(servicePlan);
        return toResponse(saved);
    }

    /**
     * Actualiza un plan de servicio existente.
     */
    @Transactional
    public ServicePlanResponse update(UUID id, ServicePlanRequest request) {
        validateCapacity(request.sessionType(), request.maxCapacity());

        ServicePlan servicePlan = findEntityById(id);

        servicePlan.setName(request.name());
        servicePlan.setDescription(request.description());
        servicePlan.setDurationMinutes(request.durationMinutes());
        servicePlan.setPriceCop(request.priceCop());
        servicePlan.setPriceUsd(request.priceUsd());
        servicePlan.setModality(request.modality());
        servicePlan.setSessionType(request.sessionType());
        servicePlan.setMaxCapacity(request.maxCapacity());
        servicePlan.setTaxRate(request.taxRate() != null ? request.taxRate() : DEFAULT_TAX_RATE);

        ServicePlan updated = servicePlanRepository.save(servicePlan);
        return toResponse(updated);
    }

    /**
     * Lista todos los planes de servicio (uso administrativo: activos e inactivos).
     */
    @Transactional(readOnly = true)
    public List<ServicePlanResponse> findAll() {
        return servicePlanRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Lista solo los planes de servicio activos (uso público).
     */
    @Transactional(readOnly = true)
    public List<ServicePlanResponse> findActive() {
        return servicePlanRepository.findByIsActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Busca un plan de servicio por ID.
     */
    @Transactional(readOnly = true)
    public ServicePlanResponse findById(UUID id) {
        return toResponse(findEntityById(id));
    }

    /**
     * Elimina lógicamente un plan de servicio (soft-delete vía isActive = false).
     */
    @Transactional
    public void softDelete(UUID id) {
        ServicePlan servicePlan = findEntityById(id);
        servicePlan.setIsActive(false);
        servicePlanRepository.save(servicePlan);
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private ServicePlan findEntityById(UUID id) {
        return servicePlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException());
    }

    /**
     * Valida la regla de capacidad condicional:
     * - INDIVIDUAL: maxCapacity debe ser null
     * - GROUP: maxCapacity es obligatorio y debe estar entre 2 y 10
     */
    private void validateCapacity(SessionType sessionType, Integer maxCapacity) {
        if (sessionType == SessionType.INDIVIDUAL && maxCapacity != null) {
            throw new BusinessRuleException(
                    "maxCapacity must be null for INDIVIDUAL sessions");
        }

        if (sessionType == SessionType.GROUP) {
            if (maxCapacity == null || maxCapacity < 2 || maxCapacity > 10) {
                throw new BusinessRuleException(
                        "maxCapacity is required for GROUP sessions and must be between 2 and 10");
            }
        }
    }

    private ServicePlanResponse toResponse(ServicePlan servicePlan) {
        return new ServicePlanResponse(
                servicePlan.getId(),
                servicePlan.getName(),
                servicePlan.getDescription(),
                servicePlan.getDurationMinutes(),
                servicePlan.getPriceCop(),
                servicePlan.getPriceUsd(),
                servicePlan.getModality(),
                servicePlan.getSessionType(),
                servicePlan.getMaxCapacity(),
                servicePlan.getTaxRate(),
                servicePlan.getIsActive(),
                servicePlan.getCreatedAt(),
                servicePlan.getUpdatedAt()
        );
    }
}