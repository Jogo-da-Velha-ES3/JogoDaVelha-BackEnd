package com.jogodavelha.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtPropertiesValidationTest.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes",
        "jwt.expiration=88800000"
})
class JwtPropertiesValidationTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void testValidConfigurationLoads() {
        assertNotNull(jwtProperties);
        assertEquals("this-is-a-very-long-secret-key-with-at-least-32-bytes", jwtProperties.secret());
        assertEquals(88800000L, jwtProperties.expiration());
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }
}
