package com.firstpay.partner.infra;

import com.firstpay.partner.api.dto.Dtos.PlatformSettingsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Properties;

/**
 * Envoi d'emails via le SMTP configuré dans les paramètres plateforme (construit à la
 * volée à partir de la base — config dynamique modifiable par l'admin sans redéploiement).
 * Best-effort : un échec d'envoi n'interrompt jamais le flux appelant (ex. création de partenaire).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final PlatformStore platform;

    public EmailService(PlatformStore platform) { this.platform = platform; }

    /** Email de connexion à un nouveau partenaire : lien de l'appli + identifiants temporaires. */
    public Mono<Boolean> sendConnectionEmail(String toEmail, String toName, String partnerName,
                                             String tempPassword) {
        if (toEmail == null || toEmail.isBlank()) return Mono.just(false);
        return platform.getRaw().flatMap(cfg -> {
            if (!cfg.smtpEnabled() || cfg.smtpHost() == null || cfg.smtpHost().isBlank()) {
                log.info("SMTP désactivé/non configuré — email de connexion à {} non envoyé (partenaire {}).", toEmail, partnerName);
                return Mono.just(false);
            }
            String subject = "Votre accès au portail FirstPay — " + partnerName;
            String body = """
                Bonjour %s,

                Votre espace partenaire « %s » vient d'être créé sur FirstPay (Afriland First Bank).

                Accédez au portail : %s

                Identifiants de première connexion :
                  • Email : %s
                  • Mot de passe temporaire : %s

                Merci de modifier votre mot de passe après la première connexion.

                — L'équipe FirstPay, Afriland First Bank
                """.formatted(toName == null ? "" : toName, partnerName,
                              cfg.appBaseUrl(), toEmail, tempPassword);
            return Mono.fromCallable(() -> { send(cfg, toEmail, subject, body); return true; })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> { log.warn("Échec d'envoi email à {} : {}", toEmail, e.getMessage()); return Mono.just(false); });
        });
    }

    /** Envoi d'un email de test (vérifie la configuration SMTP). */
    public Mono<Boolean> sendTest(String toEmail) {
        return platform.getRaw().flatMap(cfg -> {
            if (!cfg.smtpEnabled() || cfg.smtpHost() == null || cfg.smtpHost().isBlank()) {
                return Mono.just(false);
            }
            return Mono.fromCallable(() -> {
                    send(cfg, toEmail, "Test SMTP FirstPay",
                        "Ceci est un email de test envoyé depuis le portail FirstPay. Votre configuration SMTP fonctionne.");
                    return true;
                }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> { log.warn("Test SMTP échoué : {}", e.getMessage()); return Mono.just(false); });
        });
    }

    private void send(PlatformSettingsDto cfg, String to, String subject, String body) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.smtpHost());
        sender.setPort(cfg.smtpPort());
        if (cfg.smtpUsername() != null && !cfg.smtpUsername().isBlank()) sender.setUsername(cfg.smtpUsername());
        if (cfg.smtpPassword() != null && !cfg.smtpPassword().isBlank()) sender.setPassword(cfg.smtpPassword());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(cfg.smtpUsername() != null && !cfg.smtpUsername().isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(cfg.smtpUseTls()));
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");

        SimpleMailMessage msg = new SimpleMailMessage();
        String from = (cfg.smtpFromEmail() != null && !cfg.smtpFromEmail().isBlank())
            ? cfg.smtpFromEmail() : cfg.smtpUsername();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
        log.info("Email « {} » envoyé à {}", subject, to);
    }
}
