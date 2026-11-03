package com.jogodavelha.auth;

import com.jogodavelha.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private Clock clock;

    private JwtService jwtService;
    private User user;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        when(jwtProperties.secret()).thenReturn("this-is-a-very-long-secret-key-with-at-least-32-bytes");
        when(jwtProperties.expiration()).thenReturn(3600000L); // 1 hora em milissegundos

        fixedClock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        when(clock.instant()).thenReturn(fixedClock.instant());

        jwtService = new JwtService(jwtProperties, clock);

        user = new User("testuser", "$2a$10$hashedPassword", "test@example.com");
        user.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void testGenerateTokenValid() {
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
        var parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(fixedClock.instant()))
                .build();
        var claims = parser.parseSignedClaims(token).getPayload();

        assertEquals(user.getId().toString(), claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void testGenerateTokenExpirationTime() {
        String token = jwtService.generateToken(user);

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
        var parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(fixedClock.instant()))
                .build();
        var claims = parser.parseSignedClaims(token).getPayload();

        long expirationDifference = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(jwtProperties.expiration(), expirationDifference);
    }

    @Test
    void testGenerateTokenPayloadDoesNotContainSensitiveData() {
        String token = jwtService.generateToken(user);

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
        var parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(fixedClock.instant()))
                .build();
        var claims = parser.parseSignedClaims(token).getPayload();

        assertNull(claims.get("email"));
        assertNull(claims.get("username"));
        assertNull(claims.get("password"));
    }

    @Test
    void testExpiredTokenFailsValidation() {
        // Clock avançado além da expiração
        Instant futureInstant = fixedClock.instant().plusMillis(jwtProperties.expiration() + 1000);
        Clock futureClock = Clock.fixed(futureInstant, ZoneOffset.UTC);

        String token = jwtService.generateToken(user);

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
        var parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(futureClock.instant()))
                .build();

        assertThrows(ExpiredJwtException.class, () -> {
            parser.parseSignedClaims(token);
        });
    }

    @Test
    void testTokenWithWrongKeyFailsValidation() {
        String token = jwtService.generateToken(user);

        SecretKey wrongKey = Keys.hmacShaKeyFor("different-secret-key-with-at-least-32-bytes".getBytes());
        var parser = Jwts.parser()
                .verifyWith(wrongKey)
                .clock(() -> Date.from(fixedClock.instant()))
                .build();

        assertThrows(io.jsonwebtoken.security.SecurityException.class, () -> {
            parser.parseSignedClaims(token);
        });
    }
}
