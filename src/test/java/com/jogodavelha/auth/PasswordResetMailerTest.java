package com.jogodavelha.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasswordResetMailerTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final PasswordResetMailer mailer = new PasswordResetMailer(sender);

    private void configure(String url) {
        ReflectionTestUtils.setField(mailer, "resetUrl", url);
        ReflectionTestUtils.setField(mailer, "from", "no-reply@jogodavelha.local");
    }

    @Test
    void sendsEmailToRecipientWithLink() {
        configure("http://localhost:3000/reset-password");

        mailer.onRequested(new PasswordResetRequestedEvent("ana@example.com", "tok123"));

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertArrayEquals(new String[]{"ana@example.com"}, captor.getValue().getTo());
        assertTrue(captor.getValue().getText().contains("http://localhost:3000/reset-password?token=tok123"));
    }
}