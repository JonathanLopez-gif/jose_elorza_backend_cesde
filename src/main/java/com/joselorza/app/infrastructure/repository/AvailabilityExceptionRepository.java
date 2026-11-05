package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.AvailabilityException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {

    /** Usado para validar duplicados y para el endpoint público de disponibilidad. */
    Optional<AvailabilityException> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);
}