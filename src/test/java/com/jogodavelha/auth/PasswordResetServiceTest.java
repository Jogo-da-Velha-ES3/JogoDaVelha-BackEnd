package com.jogodavelha.auth;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PasswordResetService service = new PasswordResetService(users, tokens, encoder, events, clock);
    private final User user = new User("ana", encoder.encode("senhaAntiga1"), "ana@example.com");

    PasswordResetServiceTest() {
        ReflectionTestUtils.setField(service, "ttlMinutes", 30L); // vem de @Value
        user.setId(UUID.randomUUID());
    }

    private PasswordResetToken token(String raw, Instant expiresAt) {
        return new PasswordResetToken(user, PasswordResetService.sha256(raw), expiresAt, NOW);
    }

    @Test
    void validTokenChangesPassword() {
        PasswordResetToken t = token("raw", NOW.plusSeconds(600));
        when(tokens.findByTokenHash(PasswordResetService.sha256("raw"))).thenReturn(Optional.of(t));
        when(tokens.consume(any(), eq(NOW))).thenReturn(1);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        service.reset("raw", "novaSenha123", "novaSenha123");

        assertTrue(encoder.matches("novaSenha123", user.getPasswordHash()));
        assertFalse(encoder.matches("senhaAntiga1", user.getPasswordHash()));
        verify(tokens).invalidateActiveFor(user.getId(), NOW);
    }

    @Test
    void secondConcurrentRequestIsRejectedWhenConsumeUpdatesNoRow() {
        PasswordResetToken t = token("raw", NOW.plusSeconds(600));
        when(tokens.findByTokenHash(PasswordResetService.sha256("raw"))).thenReturn(Optional.of(t));
        when(tokens.consume(any(), eq(NOW))).thenReturn(0);

        assertThrows(InvalidResetTokenException.class,
                () -> service.reset("raw", "novaSenha123", "novaSenha123")
        );

        assertTrue(encoder.matches("senhaAntiga1", user.getPasswordHash()));
    }
}
