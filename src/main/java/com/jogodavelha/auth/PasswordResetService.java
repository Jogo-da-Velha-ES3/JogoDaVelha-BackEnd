package com.jogodavelha.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.password-reset.ttl-minutes:30}")
    private long ttlMinutes;

    @Transactional
    public void requestReset(String email) {
        users.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            Instant now = clock.instant();
            tokens.invalidateActiveFor(user.getId(), now);
            String raw = newToken();
            tokens.save(new PasswordResetToken(user, sha256(raw),
                    now.plus(Duration.ofMinutes(ttlMinutes)), now));
            events.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), raw));
        });
    }

    @Transactional
    public void reset(String rawToken, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) throw new PasswordMismatchException();
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHash(sha256(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(InvalidResetTokenException::new);

        // Consumo atômico: só uma das requisições concorrentes obtém 1 linha.
        if (tokens.consume(token.getId(), now) != 1) {
            throw new InvalidResetTokenException();
        }

        User user = token.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        tokens.invalidateActiveFor(user.getId(), now); // invalida este e quaisquer outros
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
