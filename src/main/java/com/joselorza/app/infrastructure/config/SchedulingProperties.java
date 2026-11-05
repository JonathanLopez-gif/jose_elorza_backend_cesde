package com.joselorza.app.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Reglas configurables del motor de agenda (plazos, penalizaciones y jobs).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.scheduling")
public class SchedulingProperties {

    // Zona horaria en la que se interpretan las AvailabilityRule (las citas siempre se guardan en UTC)
    private String providerTimezone = "America/Bogota";

    // Plazo para pagar una reserva o una penalidad antes de que expire
    private int paymentDeadlineHours = 48;

    // Con menos de estos días de anticipación se aplica penalización
    private int penaltyThresholdDays = 7;

    // Porcentaje de penalización sobre el monto pagado (0.10 = 10%)
    private BigDecimal penaltyRate = new BigDecimal("0.10");

    private int reminderHoursBefore = 24;

    // Margen tras la finalización de la cita antes de marcarla como NO_SHOW
    private int noShowGraceHours = 24;

    // Cada cuántos minutos se ofrece un horario de inicio dentro de un bloque de disponibilidad
    private int slotStepMinutes = 60;

    // Anticipación mínima para reservar o reagendar (el usuario; el admin no tiene este límite)
    private int minNoticeHours = 12;

    public ZoneId providerZone() {
        return ZoneId.of(providerTimezone);
    }
}
