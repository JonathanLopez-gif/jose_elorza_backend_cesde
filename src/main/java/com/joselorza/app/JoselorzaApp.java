package com.joselorza.app;

import com.joselorza.app.infrastructure.adapter.calendar.GoogleCalendarProperties;
import com.joselorza.app.infrastructure.adapter.payment.Global66Properties;
import com.joselorza.app.infrastructure.adapter.payment.PaymentSandboxProperties;
import com.joselorza.app.infrastructure.adapter.payment.WompiProperties;
import com.joselorza.app.infrastructure.config.AppProperties;
import com.joselorza.app.infrastructure.config.SchedulingProperties;
import com.joselorza.app.infrastructure.security.JwtProperties;
import com.joselorza.app.infrastructure.seed.AdminSeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Jobs: recordatorios 24h, NO_SHOW y expiración de pagos
@EnableAsync      // Envío de correos y sincronización con Google Calendar fuera del hilo HTTP
@EnableConfigurationProperties(
        {JwtProperties.class, AdminSeedProperties.class, AppProperties.class, SchedulingProperties.class,
                WompiProperties.class, Global66Properties.class, PaymentSandboxProperties.class,
                GoogleCalendarProperties.class}
)
public class JoselorzaApp {

    public static void main(String[] args) {

        SpringApplication.run(JoselorzaApp.class, args);
    }

}
