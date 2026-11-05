package com.joselorza.app.application.service;

import com.joselorza.app.domain.model.Appointment;
import com.joselorza.app.domain.model.Payment;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.domain.model.enumerated.PaymentType;
import com.joselorza.app.infrastructure.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Construye el contenido (asunto + HTML) de los correos enviados a los usuarios.
 * Las fechas se presentan en la zona horaria del usuario.
 */
@Component
@RequiredArgsConstructor
public class EmailTemplateBuilder {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, hh:mm a (z)", new Locale("es", "CO"));

    private final AppProperties appProperties;

    public record EmailContent(String subject, String html) {
    }

    public EmailContent bookingConfirmed(Appointment appointment) {
        return build("Tu cita ha sido confirmada",
                "Hola " + appointment.getUser().getName() + ", tu reserva quedó confirmada.",
                appointmentDetails(appointment, appointment.getScheduledAt()),
                "Si necesitas reagendar o cancelar, hazlo desde tu panel. Recuerda que con menos de 7 días "
                        + "de anticipación aplica una penalización del 10%.");
    }

    public EmailContent rescheduled(Appointment appointment) {
        return build("Tu cita ha sido reagendada",
                "Hola " + appointment.getUser().getName() + ", tu cita fue reagendada con éxito.",
                appointmentDetails(appointment, appointment.getScheduledAt()),
                null);
    }

    public EmailContent cancelled(Appointment appointment) {
        Map<String, String> rows = appointmentDetails(appointment, appointment.getScheduledAt());
        rows.remove("Enlace de la sesión");
        if (appointment.getCancellationReason() != null) {
            rows.put("Motivo", appointment.getCancellationReason());
        }
        if (Boolean.TRUE.equals(appointment.getPenaltyApplied()) && appointment.getPenaltyAmount() != null) {
            rows.put("Penalización", appointment.getPenaltyAmount().toPlainString()
                    + " (se descuenta del reembolso)");
        }
        return build("Tu cita ha sido cancelada",
                "Hola " + appointment.getUser().getName() + ", te confirmamos la cancelación de tu cita.",
                rows,
                "Si aplica un reembolso, te notificaremos cuando sea procesado.");
    }

    public EmailContent reminder(Appointment appointment) {
        return build("Recordatorio: tu cita es mañana",
                "Hola " + appointment.getUser().getName() + ", te recordamos que tienes una cita en las próximas 24 horas.",
                appointmentDetails(appointment, appointment.getScheduledAt()),
                null);
    }

    public EmailContent paymentSuccess(Payment payment) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Servicio", payment.getAppointment().getServicePlan().getName());
        rows.put("Concepto", payment.getPaymentType() == PaymentType.PENALTY ? "Penalización" : "Reserva");
        rows.put("Monto", money(payment.getAmount(), payment.getCurrency().name()));
        rows.put("Pasarela", payment.getGateway().name());
        rows.put("No. de pago", payment.getId().toString());
        return build("Pago recibido",
                "Hola " + payment.getUser().getName() + ", hemos recibido tu pago correctamente.",
                rows,
                "Puedes descargar tu comprobante en PDF desde tu historial de pagos.");
    }

    public EmailContent refund(Payment payment) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Servicio", payment.getAppointment().getServicePlan().getName());
        rows.put("Monto pagado", money(payment.getAmount(), payment.getCurrency().name()));
        rows.put("Monto reembolsado", money(payment.getRefundedAmount(), payment.getCurrency().name()));
        if (payment.getRefundReason() != null) {
            rows.put("Observación", payment.getRefundReason());
        }
        return build("Reembolso registrado",
                "Hola " + payment.getUser().getName() + ", registramos el reembolso de tu pago.",
                rows,
                "La devolución del dinero se realiza por fuera de la plataforma y puede tardar algunos días hábiles.");
    }

    public EmailContent passwordReset(User user, String rawToken, long expirationMinutes) {
        String link = appProperties.getFrontendUrl() + "/restablecer?token=" + rawToken;
        String html = layout("Recupera tu contraseña",
                "<p>Hola " + escape(user.getName()) + ", recibimos una solicitud para restablecer tu contraseña.</p>"
                        + "<p><a href=\"" + escape(link) + "\" style=\"background:#212529;color:#fff;padding:10px 18px;"
                        + "border-radius:4px;text-decoration:none\">Restablecer contraseña</a></p>"
                        + "<p>El enlace expira en " + expirationMinutes + " minutos. "
                        + "Si no realizaste esta solicitud, ignora este mensaje.</p>");
        return new EmailContent("Recupera tu contraseña", html);
    }

    // ---------------------------------------------------------------
    // Métodos privados de apoyo
    // ---------------------------------------------------------------

    private Map<String, String> appointmentDetails(Appointment appointment, Instant at) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Servicio", appointment.getServicePlan().getName());
        rows.put("Fecha y hora", formatDate(at, appointment.getUserTimezone()));
        rows.put("Duración", appointment.getDurationMinutes() + " minutos");
        rows.put("Modalidad", appointment.getServicePlan().getModality().name());
        if (appointment.getMeetingLink() != null) {
            rows.put("Enlace de la sesión", appointment.getMeetingLink());
        }
        return rows;
    }

    private EmailContent build(String subject, String intro, Map<String, String> rows, String footer) {
        StringBuilder body = new StringBuilder("<p>").append(escape(intro)).append("</p>");
        body.append("<table style=\"border-collapse:collapse;width:100%\">");
        rows.forEach((label, value) -> body
                .append("<tr><td style=\"padding:6px;background:#f0f0f0;font-weight:bold;width:35%\">")
                .append(escape(label)).append("</td><td style=\"padding:6px\">")
                .append(value.startsWith("http") ? "<a href=\"" + escape(value) + "\">" + escape(value) + "</a>" : escape(value))
                .append("</td></tr>"));
        body.append("</table>");
        if (footer != null) {
            body.append("<p>").append(escape(footer)).append("</p>");
        }
        return new EmailContent(subject, layout(subject, body.toString()));
    }

    private String layout(String title, String content) {
        return """
                <html><body style="font-family:Arial,sans-serif;color:#212529;max-width:600px;margin:auto">
                <h2 style="border-bottom:2px solid #212529;padding-bottom:8px">%s</h2>
                %s
                <p style="color:#888;font-size:12px;margin-top:24px">%s</p>
                </body></html>
                """.formatted(escape(title), content, escape(appProperties.getMail().getFromName()));
    }

    private String formatDate(Instant instant, String timezone) {
        return instant.atZone(ZoneId.of(timezone)).format(DATE_TIME);
    }

    private String money(BigDecimal amount, String currency) {
        return amount == null ? "-" : amount.toPlainString() + " " + currency;
    }

    private String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
