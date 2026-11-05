package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.GoogleCalendarCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface GoogleCalendarCredentialRepository extends JpaRepository<GoogleCalendarCredential, UUID> {
    // Token único del sistema: se usa siempre la credencial más reciente
    Optional<GoogleCalendarCredential> findFirstByOrderByUpdatedAtDesc();
}
