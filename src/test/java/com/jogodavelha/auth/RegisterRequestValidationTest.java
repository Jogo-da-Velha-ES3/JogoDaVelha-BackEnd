package com.jogodavelha.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRegisterRequest() {
        RegisterRequest request = new RegisterRequest("validuser", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Request válido não deve ter violações");
    }

    @Test
    void testUsernameTooShort() {
        RegisterRequest request = new RegisterRequest("ab", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("3 e 15")));
    }

    @Test
    void testUsernameTooLong() {
        RegisterRequest request = new RegisterRequest("thisusernameistoolong", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("3 e 15")));
    }

    @Test
    void testUsernameMinimumLength() {
        RegisterRequest request = new RegisterRequest("abc", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Username com 3 caracteres deve ser válido");
    }

    @Test
    void testUsernameMaximumLength() {
        RegisterRequest request = new RegisterRequest("abcde123456789", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Username com 15 caracteres deve ser válido");
    }

    @Test
    void testUsernameWithSpecialCharacters() {
        RegisterRequest request = new RegisterRequest("user@#$%", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Username com caracteres especiais deve ser válido");
    }

    @Test
    void testUsernameWithEmoji() {
        RegisterRequest request = new RegisterRequest("user😀", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Username com emoji deve ser válido");
    }

    @Test
    void testUsernameBlank() {
        RegisterRequest request = new RegisterRequest("   ", "password123", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("obrigatório")));
    }

    @Test
    void testPasswordTooShort() {
        RegisterRequest request = new RegisterRequest("validuser", "12345", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("6 e 72")));
    }

    @Test
    void testPasswordMinimumLength() {
        RegisterRequest request = new RegisterRequest("validuser", "123456", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Senha com 6 caracteres deve ser válida");
    }

    @Test
    void testPasswordBlank() {
        RegisterRequest request = new RegisterRequest("validuser", "   ", "valid@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("obrigatória")));
    }

    @Test
    void testEmailMissingAtSymbol() {
        RegisterRequest request = new RegisterRequest("validuser", "password123", "invalidemail.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testEmailWithoutTLD() {
        RegisterRequest request = new RegisterRequest("validuser", "password123", "a@b");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("TLD")));
    }

    @Test
    void testEmailValid() {
        RegisterRequest request = new RegisterRequest("validuser", "password123", "user@example.com");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "E-mail válido não deve ter violações");
    }

    @Test
    void testEmailBlank() {
        RegisterRequest request = new RegisterRequest("validuser", "password123", "   ");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("obrigatório")));
    }

    @Test
    void testEmailTooLong() {
        String longEmail = "a".repeat(200) + "@" + "b".repeat(50) + ".com";
        RegisterRequest request = new RegisterRequest("validuser", "password123", longEmail);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("254")));
    }

    @Test
    void testToStringExcludesPassword() {
        RegisterRequest request = new RegisterRequest("validuser", "password123", "valid@example.com");
        String toString = request.toString();
        assertFalse(toString.contains("password123"), "toString não deve conter a senha");
        assertTrue(toString.contains("validuser"), "toString deve conter o username");
        assertTrue(toString.contains("valid@example.com"), "toString deve conter o e-mail");
    }
}
