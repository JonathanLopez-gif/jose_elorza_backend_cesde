package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.ServicePlanService;
import com.joselorza.app.presentation.dto.request.ServicePlanRequest;
import com.joselorza.app.presentation.dto.response.ServicePlanResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller administrativo para gestión completa (CRUD) de planes de servicio.
 * Acceso restringido a usuarios con rol ADMIN mediante @PreAuthorize.
 */
@RestController
@RequestMapping("/api/admin/service-plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminServicePlanController {

    private final ServicePlanService servicePlanService;

    /**
     * Lista todos los planes de servicio (activos e inactivos).
     */
    @GetMapping
    public List<ServicePlanResponse> getAllPlans() {
        return servicePlanService.findAll();
    }

    /**
     * Obtiene un plan de servicio por ID.
     */
    @GetMapping("/{id}")
    public ServicePlanResponse getPlanById(@PathVariable UUID id) {
        return servicePlanService.findById(id);
    }

    /**
     * Crea un nuevo plan de servicio.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServicePlanResponse createPlan(@Valid @RequestBody ServicePlanRequest request) {
        return servicePlanService.create(request);
    }

    /**
     * Actualiza un plan de servicio existente.
     */
    @PutMapping("/{id}")
    public ServicePlanResponse updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ServicePlanRequest request) {
        return servicePlanService.update(id, request);
    }

    /**
     * Elimina lógicamente un plan de servicio (soft-delete: isActive = false).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable UUID id) {
        servicePlanService.softDelete(id);
    }
}