package com.joselorza.app.presentation.dto.request;

import com.joselorza.app.domain.model.enumerated.SocialPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SocialLinkRequest(

        @NotNull(message = "The platform is required")
        SocialPlatform platform,

        @NotBlank(message = "The name is required")
        @Size(max = 80, message = "The name can't exceed 80 characters")
        String name,

        @Size(max = 255, message = "The description can't exceed 255 characters")
        String description,

        @NotBlank(message = "The URL is required")
        @Size(max = 500, message = "The URL can't exceed 500 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "The URL must start with http:// or https://")
        String url,

        Boolean isActive // opcional; si es null en la creación se asigna true

) {
}
