package com.jogodavelha.auth;

import com.jogodavelha.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceExtractUserIdTest {
    private static final String SECRET = "this-is-a-very-long-secret-key-with-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final JwtProperties properties = new JwtProperties(SECRET, 86_400_000L);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService service = new JwtService(properties, clock);
    private final UUID id = UUID.randomUUID();

    @Test void validTokenReturnsSubject() {
        User user = new User("user", "password", "user@example.com"); user.setId(id);
        assertEquals(java.util.Optional.of(id), service.extractUserId(service.generateToken(user)));
    }

    @Test void rejectedTokensReturnEmpty() {
        String expired = signed(id.toString(), NOW.minusSeconds(120), NOW.minusSeconds(60), SECRET);
        String wrongKey = signed(id.toString(), NOW.minusSeconds(1), NOW.plusSeconds(60),
                "different-secret-key-with-at-least-32-bytes");
        String noSubject = signed(null, NOW.minusSeconds(1), NOW.plusSeconds(60), SECRET);
        String invalidSubject = signed("not-a-uuid", NOW.minusSeconds(1), NOW.plusSeconds(60), SECRET);
        for (String token : new String[]{expired, wrongKey, "garbage", "", noSubject, invalidSubject}) {
            assertTrue(service.extractUserId(token).isEmpty());
        }
    }

    private String signed(String subject, Instant issuedAt, Instant expiresAt, String secret) {
        var builder = Jwts.builder().issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt));
        if (subject != null) builder.subject(subject);
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }
}
