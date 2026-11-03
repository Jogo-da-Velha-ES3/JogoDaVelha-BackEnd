package com.jogodavelha.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void testValidLoginRequest() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testIdentifierBlank() {
        LoginRequest request = new LoginRequest("", "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Identifier é obrigatório")));
    }

    @Test
    void testIdentifierNull() {
        LoginRequest request = new LoginRequest(null, "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Identifier é obrigatório")));
    }

    @Test
    void testIdentifierTooLong() {
        String longIdentifier = "a".repeat(255);
        LoginRequest request = new LoginRequest(longIdentifier, "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Identifier deve ter no máximo 254 caracteres")));
    }

    @Test
    void testIdentifierExactly254Characters() {
        String identifier = "a".repeat(254);
        LoginRequest request = new LoginRequest(identifier, "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testPasswordBlank() {
        LoginRequest request = new LoginRequest("test@example.com", "");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Senha é obrigatória")));
    }

    @Test
    void testPasswordNull() {
        LoginRequest request = new LoginRequest("test@example.com", null);

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Senha é obrigatória")));
    }

    @Test
    void testShortPasswordIsValidInLogin() {
        // No login, senhas curtas são válidas (diferente do cadastro)
        LoginRequest request = new LoginRequest("test@example.com", "123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testUsernameAsIdentifier() {
        LoginRequest request = new LoginRequest("testuser", "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testIdentifierWithSpaces() {
        LoginRequest request = new LoginRequest("  test@example.com  ", "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testPasswordNotLogged() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        String toString = request.toString();

        assertNotNull(toString);
        assertFalse(toString.contains("password123"));
        assertTrue(toString.contains("test@example.com"));
    }
}
