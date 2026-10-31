package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.ServicePlanService;
import com.joselorza.app.presentation.dto.response.ServicePlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller público para consulta de planes de servicio activos.
 * No requiere autenticación.
 */
@RestController
@RequestMapping("/api/service-plans")
@RequiredArgsConstructor
public class ServicePlanController {

    private final ServicePlanService servicePlanService;

    /**
     * Lista los planes de servicio activos disponibles para el público.
     */
    @GetMapping
    public List<ServicePlanResponse> getActivePlans() {
        return servicePlanService.findActive();
    }
}