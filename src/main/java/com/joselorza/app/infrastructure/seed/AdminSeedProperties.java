package com.joselorza.app.infrastructure.seed;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "admin.seed")
public class AdminSeedProperties {
    private String email;
    private String password;
    private String name;
    private String timezone;
}