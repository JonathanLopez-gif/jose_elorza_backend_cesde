package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.SocialLink;
import com.joselorza.app.infrastructure.repository.SocialLinkRepository;
import com.joselorza.app.presentation.dto.request.SocialLinkRequest;
import com.joselorza.app.presentation.dto.response.SocialLinkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gestión de los canales externos (redes sociales) que se muestran en el sitio.
 */
@Service
@RequiredArgsConstructor
public class SocialLinkService {

    private final SocialLinkRepository repository;

    @Transactional(readOnly = true)
    public List<SocialLinkResponse> findActive() {
        return repository.findByIsActiveTrueOrderByDisplayOrderAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SocialLinkResponse> findAll() {
        return repository.findAllByOrderByDisplayOrderAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public SocialLinkResponse create(SocialLinkRequest request) {
        SocialLink link = SocialLink.builder()
                .platform(request.platform())
                .name(request.name().trim())
                .description(request.description())
                .url(request.url().trim())
                .isActive(request.isActive() == null || request.isActive())
                .displayOrder(repository.findMaxDisplayOrder() + 1) // Se agrega al final
                .build();

        return toResponse(repository.save(link));
    }

    @Transactional
    public SocialLinkResponse update(UUID id, SocialLinkRequest request) {
        SocialLink link = findEntityById(id);

        link.setPlatform(request.platform());
        link.setName(request.name().trim());
        link.setDescription(request.description());
        link.setUrl(request.url().trim());
        if (request.isActive() != null) {
            link.setIsActive(request.isActive());
        }

        return toResponse(repository.save(link));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(findEntityById(id));
        renumber(repository.findAllByOrderByDisplayOrderAsc()); // Sin huecos en el orden
    }

    /**
     * Aplica un nuevo orden. La lista debe contener exactamente todos los canales.
     */
    @Transactional
    public List<SocialLinkResponse> reorder(List<UUID> ids) {
        List<SocialLink> links = repository.findAll();

        if (ids.size() != links.size() || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessRuleException("The order must include every social link exactly once");
        }

        Map<UUID, SocialLink> byId = links.stream().collect(Collectors.toMap(SocialLink::getId, Function.identity()));
        List<SocialLink> ordered = ids.stream()
                .map(id -> {
                    SocialLink link = byId.get(id);
                    if (link == null) {
                        throw new ResourceNotFoundException();
                    }
                    return link;
                })
                .toList();

        renumber(ordered);
        return ordered.stream().map(this::toResponse).toList();
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private void renumber(List<SocialLink> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setDisplayOrder(i + 1);
        }
        repository.saveAll(ordered);
    }

    private SocialLink findEntityById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
    }

    private SocialLinkResponse toResponse(SocialLink link) {
        return new SocialLinkResponse(
                link.getId(),
                link.getPlatform(),
                link.getName(),
                link.getDescription(),
                link.getUrl(),
                Boolean.TRUE.equals(link.getIsActive()),
                link.getDisplayOrder()
        );
    }
}
