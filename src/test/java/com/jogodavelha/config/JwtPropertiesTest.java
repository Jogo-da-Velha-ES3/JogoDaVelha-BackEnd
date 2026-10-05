package com.jogodavelha.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtPropertiesTest {

    @Test
    void testValidJwtProperties() {
        JwtProperties properties = new JwtProperties(
                "this-is-a-very-long-secret-key-with-at-least-32-bytes",
                3600000L
        );

        assertNotNull(properties);
        assertEquals("this-is-a-very-long-secret-key-with-at-least-32-bytes", properties.secret());
        assertEquals(3600000L, properties.expiration());
    }

    @Test
    void testSecretTooShort() {
        JwtProperties properties = new JwtProperties(
                "short",
                3600000L
        );

        // A validação deve falhar se o secret for menor que 32 bytes
        // Isso será testado via @Validated no contexto Spring
        assertNotNull(properties);
        assertEquals("short", properties.secret());
    }

    @Test
    void testSecretExactly32Bytes() {
        String secret32Bytes = "12345678901234567890123456789012"; // Exatamente 32 bytes
        JwtProperties properties = new JwtProperties(secret32Bytes, 3600000L);

        assertNotNull(properties);
        assertEquals(secret32Bytes, properties.secret());
    }

    @Test
    void testExpirationZero() {
        JwtProperties properties = new JwtProperties(
                "this-is-a-very-long-secret-key-with-at-least-32-bytes",
                0L
        );

        assertNotNull(properties);
        assertEquals(0L, properties.expiration());
    }

    @Test
    void testExpirationNegative() {
        JwtProperties properties = new JwtProperties(
                "this-is-a-very-long-secret-key-with-at-least-32-bytes",
                -1L
        );

        assertNotNull(properties);
        assertEquals(-1L, properties.expiration());
    }

    @Test
    void testExpirationPositive() {
        JwtProperties properties = new JwtProperties(
                "this-is-a-very-long-secret-key-with-at-least-32-bytes",
                88800000L
        );

        assertNotNull(properties);
        assertEquals(88800000L, properties.expiration());
    }
}
