package com.joselorza.app.infrastructure.repository;

import com.joselorza.app.domain.model.SocialLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface SocialLinkRepository extends JpaRepository<SocialLink, UUID> {

    /** Listado administrativo (activos e inactivos) en el orden de visualización. */
    List<SocialLink> findAllByOrderByDisplayOrderAsc();

    /** Listado público: solo canales activos. */
    List<SocialLink> findByIsActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT COALESCE(MAX(s.displayOrder), 0) FROM SocialLink s")
    int findMaxDisplayOrder();
}
