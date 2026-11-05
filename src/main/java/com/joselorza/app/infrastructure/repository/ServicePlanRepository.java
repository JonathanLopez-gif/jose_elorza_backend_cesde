package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.ServicePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio para la gestión de planes de servicio.
 * Spring Data JPA genera la implementación automáticamente.
 */
@Repository
public interface ServicePlanRepository extends JpaRepository<ServicePlan, UUID> {

    /**
     * Devuelve solo los planes activos, usados en el endpoint público.
     */
    List<ServicePlan> findByIsActiveTrue();
}