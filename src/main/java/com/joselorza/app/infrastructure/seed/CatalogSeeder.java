package com.joselorza.app.infrastructure.seed;

import com.joselorza.app.domain.model.ServicePlan;
import com.joselorza.app.domain.model.SocialLink;
import com.joselorza.app.domain.model.enumerated.Modality;
import com.joselorza.app.domain.model.enumerated.SessionType;
import com.joselorza.app.domain.model.enumerated.SocialPlatform;
import com.joselorza.app.infrastructure.repository.ServicePlanRepository;
import com.joselorza.app.infrastructure.repository.SocialLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Carga inicial del catálogo oficial (Portafolio de Servicios) y de las redes sociales.
 * Solo actúa si la tabla correspondiente está vacía (base de datos nueva): después,
 * el administrador gestiona servicios y redes desde el panel sin que se sobrescriban.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogSeeder implements CommandLineRunner {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");

    private final ServicePlanRepository servicePlanRepository;
    private final SocialLinkRepository socialLinkRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedServicePlans();
        seedSocialLinks();
    }

    private void seedServicePlans() {
        if (servicePlanRepository.count() > 0) {
            return;
        }

        ServicePlan conversaciones = ServicePlan.builder()
                .name("Sesiones individuales: Conversaciones conscientes")
                .description("""
                        Un porcentaje muy grande del proceso de sanación corresponde al poder expresar todo \
                        lo que sentimos que nos está afectando. Los humanos somos seres conversacionales. \
                        Las conversaciones privadas y públicas son fundamentales para construirnos. Siempre \
                        tenemos una historia que contar, permíteme escucharla para poderte guiar en tu \
                        proceso de sanación.

                        ¿Qué incluye?
                        • Sesión individual (duración 2 horas).

                        Modalidad: virtual o presencial.""")
                .durationMinutes(120)
                .priceCop(baseFromTotal("290000"))
                .priceUsd(baseFromTotal("80"))
                .modality(Modality.LIVE)
                .sessionType(SessionType.INDIVIDUAL)
                .taxRate(TAX_RATE)
                .isActive(true)
                .build();

        ServicePlan biohipnosis = ServicePlan.builder()
                .name("Consultas individuales: Biohipnosis Sistémica")
                .description("""
                        Es una metodología que fusiona componentes de las constelaciones familiares, la \
                        biodescodificación y la hipnosis. Acompaño a quienes sienten el llamado a sanar su \
                        historia y reconectarse con su poder interior. Mi mayor satisfacción es ver cómo, \
                        cuando una persona sana, su entorno también florece.

                        ¿Qué incluye?
                        • Entrevista inicial (duración 30 minutos) para revisar el formulario enviado al \
                        momento de agendar la consulta, que incluye preguntas minuciosas y dirigidas sobre los \
                        conflictos de la vida y temas de contexto que contribuyen a preparar mejor la consulta \
                        y maximizar el tiempo de esta.
                        • Consulta de Biohipnosis Sistémica (duración 2 horas).
                        • Sesión de seguimiento (duración 1 hora y 30 minutos).

                        Modalidad: virtual.""")
                .durationMinutes(240)
                .priceCop(baseFromTotal("370000"))
                .priceUsd(baseFromTotal("100"))
                .modality(Modality.LIVE)
                .sessionType(SessionType.INDIVIDUAL)
                .taxRate(TAX_RATE)
                .isActive(true)
                .build();

        servicePlanRepository.saveAll(List.of(conversaciones, biohipnosis));
        log.info("Initial service catalog created (2 plans).");
    }

    private void seedSocialLinks() {
        if (socialLinkRepository.count() > 0) {
            return;
        }

        socialLinkRepository.saveAll(List.of(
                SocialLink.builder()
                        .platform(SocialPlatform.YOUTUBE)
                        .name("YouTube")
                        .description("EmprenSer Podcast")
                        .url("https://www.youtube.com/@EmprenSerPodcast")
                        .isActive(true)
                        .displayOrder(1)
                        .build(),
                SocialLink.builder()
                        .platform(SocialPlatform.INSTAGRAM)
                        .name("Instagram")
                        .description("@josef.elorza")
                        .url("https://www.instagram.com/josef.elorza")
                        .isActive(true)
                        .displayOrder(2)
                        .build(),
                SocialLink.builder()
                        .platform(SocialPlatform.WHATSAPP)
                        .name("WhatsApp")
                        .description("+57 315 462 7900")
                        .url("https://wa.me/573154627900")
                        .isActive(true)
                        .displayOrder(3)
                        .build()
        ));
        log.info("Initial social links created.");
    }

    /**
     * Los precios del portafolio ya incluyen IVA; la BD guarda la base sin IVA
     * (el frontend y los pagos vuelven a sumar el 19 %).
     */
    private static BigDecimal baseFromTotal(String total) {
        return new BigDecimal(total).divide(BigDecimal.ONE.add(TAX_RATE), 2, RoundingMode.HALF_UP);
    }
}
