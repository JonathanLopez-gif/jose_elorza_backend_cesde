package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AvailabilityRuleService;
import com.joselorza.app.presentation.dto.request.AvailabilityRuleRequest;
import com.joselorza.app.presentation.dto.response.AvailabilityRuleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/availability-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAvailabilityRuleController {

    private final AvailabilityRuleService service;

    @GetMapping
    public List<AvailabilityRuleResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AvailabilityRuleResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityRuleResponse create(@Valid @RequestBody AvailabilityRuleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AvailabilityRuleResponse update(@PathVariable UUID id, @Valid @RequestBody AvailabilityRuleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}