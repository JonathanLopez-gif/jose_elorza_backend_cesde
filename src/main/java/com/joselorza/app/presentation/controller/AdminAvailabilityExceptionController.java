package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.AvailabilityExceptionService;
import com.joselorza.app.presentation.dto.request.AvailabilityExceptionRequest;
import com.joselorza.app.presentation.dto.response.AvailabilityExceptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/availability-exceptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAvailabilityExceptionController {

    private final AvailabilityExceptionService service;

    @GetMapping
    public List<AvailabilityExceptionResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AvailabilityExceptionResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityExceptionResponse create(@Valid @RequestBody AvailabilityExceptionRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AvailabilityExceptionResponse update(@PathVariable UUID id, @Valid @RequestBody AvailabilityExceptionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}