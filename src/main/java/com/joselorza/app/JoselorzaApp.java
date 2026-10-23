package com.joselorza.app;

import com.joselorza.app.infrastructure.security.JwtProperties;
import com.joselorza.app.infrastructure.seed.AdminSeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(
        {JwtProperties.class, AdminSeedProperties.class}
)
public class JoselorzaApp {

    public static void main(String[] args) {

        SpringApplication.run(JoselorzaApp.class, args);
    }

}
