package com.joselorza.app.infrastructure.adapter.mail;

import com.joselorza.app.application.port.out.EmailSenderPort;
import com.joselorza.app.domain.exception.ExternalServiceException;
import com.joselorza.app.infrastructure.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envío de correos vía SMTP (Gmail con dominio joselorza.com en producción, MailDev en local).
 */
@Component
@RequiredArgsConstructor
public class SmtpEmailAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.getMail().getFrom(), appProperties.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            throw new ExternalServiceException("Could not send email to " + to, e);
        }
    }
}
