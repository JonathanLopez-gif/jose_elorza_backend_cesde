package com.joselorza.app.presentation.controller;

import com.joselorza.app.application.service.SocialLinkService;
import com.joselorza.app.presentation.dto.response.SocialLinkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Canales externos activos, en orden (público, sin autenticación).
 */
@RestController
@RequestMapping("/api/social-links")
@RequiredArgsConstructor
public class SocialLinkController {

    private final SocialLinkService socialLinkService;

    @GetMapping
    public List<SocialLinkResponse> getActiveLinks() {
        return socialLinkService.findActive();
    }
}
