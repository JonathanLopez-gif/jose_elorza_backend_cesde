package com.joselorza.app.presentation.dto.response;

import com.joselorza.app.domain.model.enumerated.SocialPlatform;

import java.util.UUID;

public record SocialLinkResponse(
        UUID id,
        SocialPlatform platform,
        String name,
        String description,
        String url,
        boolean active,
        int order
) {
}
