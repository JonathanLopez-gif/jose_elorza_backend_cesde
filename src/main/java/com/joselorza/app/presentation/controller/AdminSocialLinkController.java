package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.SocialLinkService;
import com.joselorza.app.presentation.dto.request.SocialLinkOrderRequest;
import com.joselorza.app.presentation.dto.request.SocialLinkRequest;
import com.joselorza.app.presentation.dto.response.SocialLinkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/social-links")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSocialLinkController {

    private final SocialLinkService service;

    @GetMapping
    public List<SocialLinkResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SocialLinkResponse create(@Valid @RequestBody SocialLinkRequest request) {
        return service.create(request);
    }

    // Declarado antes de /{id} para que "order" no se interprete como un id
    @PutMapping("/order")
    public List<SocialLinkResponse> reorder(@Valid @RequestBody SocialLinkOrderRequest request) {
        return service.reorder(request.ids());
    }

    @PutMapping("/{id}")
    public SocialLinkResponse update(@PathVariable UUID id, @Valid @RequestBody SocialLinkRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
