package com.jogodavelha.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetMailer {
    private final JavaMailSender mailSender;
    @Value("${app.frontend.reset-password-url}") private String resetUrl;
    @Value("${spring.mail.from:no-reply@jogodavelha.local}") private String from;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(PasswordResetRequestedEvent event) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(event.email());
            msg.setSubject("Recuperação de senha - Jogo da Velha");
            msg.setText("Para redefinir sua senha, acesse:\n" + buildLink(event.rawToken())
                    + "\n\nSe você não solicitou, ignore este e-mail.");
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de recuperação"); // sem token nem endereço
        }
    }

    String buildLink(String token) {
        return resetUrl + (resetUrl.contains("?") ? "&" : "?") + "token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}