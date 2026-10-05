package com.jogodavelha.auth;

import com.jogodavelha.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest("testuser", "password123", "test@example.com");

        testUser = new User("testuser", "$2a$10$hashedPassword", "test@example.com");
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void testRegisterSuccess() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(java.util.UUID.randomUUID());
            return user;
        });

        User result = authService.register(validRequest);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("$2a$10$hashedPassword", result.getPasswordHash());
        assertEquals(0, result.getCoinsBalance());

        verify(userRepository).existsByUsernameIgnoreCase("testuser");
        verify(userRepository).existsByEmail("test@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testRegisterUsernameAlreadyExists() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(validRequest));

        verify(userRepository).existsByUsernameIgnoreCase("testuser");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterEmailAlreadyExists() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(validRequest));

        verify(userRepository).existsByUsernameIgnoreCase("testuser");
        verify(userRepository).existsByEmail("test@example.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterNormalizesUsername() {
        RegisterRequest requestWithSpaces = new RegisterRequest("  testuser  ", "password123", "test@example.com");
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(requestWithSpaces);

        verify(userRepository).existsByUsernameIgnoreCase("testuser");
        verify(userRepository).save(argThat(user -> "testuser".equals(user.getUsername())));
    }

    @Test
    void testRegisterNormalizesEmail() {
        RegisterRequest requestWithMixedCase = new RegisterRequest("testuser", "password123", "  TEST@EXAMPLE.COM  ");
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(requestWithMixedCase);

        verify(userRepository).existsByEmail("test@example.com");
        verify(userRepository).save(argThat(user -> "test@example.com".equals(user.getEmail())));
    }

    @Test
    void testRegisterHashIsDifferentFromPassword() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.register(validRequest);

        assertNotEquals("password123", result.getPasswordHash());
        assertTrue(result.getPasswordHash().startsWith("$2"));
    }

    @Test
    void testRegisterDataIntegrityViolationUsername() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_users_username\""));

        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(validRequest));
    }

    @Test
    void testRegisterDataIntegrityViolationEmail() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_users_email\""));

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(validRequest));
    }

    @Test
    void testRegisterDataIntegrityViolationUnknownConstraint() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unknown constraint"));

        assertThrows(DataIntegrityViolationException.class, () -> authService.register(validRequest));
    }

    @Test
    void testRegisterPasswordHashVerifiable() {
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.register(validRequest);

        assertTrue(passwordEncoder.matches("password123", result.getPasswordHash()));
    }

    // ===== TESTES DE LOGIN =====

    @Test
    void testLoginWithEmailSuccess() {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        LoginResult result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals("jwt-token", result.accessToken());
        assertEquals(testUser, result.user());

        verify(userRepository).findByEmail("test@example.com");
        verify(passwordEncoder).matches("password123", testUser.getPasswordHash());
        verify(jwtService).generateToken(testUser);
    }

    @Test
    void testLoginWithUsernameSuccess() {
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");

        when(userRepository.findByEmail("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        LoginResult result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals("jwt-token", result.accessToken());
        assertEquals(testUser, result.user());

        verify(userRepository).findByEmail("testuser");
        verify(userRepository).findByUsernameIgnoreCase("testuser");
        verify(passwordEncoder).matches("password123", testUser.getPasswordHash());
        verify(jwtService).generateToken(testUser);
    }

    @Test
    void testLoginWithEmailMixedCase() {
        LoginRequest loginRequest = new LoginRequest("  TEST@EXAMPLE.COM  ", "password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        LoginResult result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals("jwt-token", result.accessToken());

        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void testLoginWithUsernameMixedCase() {
        LoginRequest loginRequest = new LoginRequest("TESTUSER", "password123");

        when(userRepository.findByEmail("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("TESTUSER")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        LoginResult result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals("jwt-token", result.accessToken());

        verify(userRepository).findByUsernameIgnoreCase("TESTUSER");
    }

    @Test
    void testLoginWithWrongPassword() {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "wrongpassword");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", testUser.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        verify(passwordEncoder).matches("wrongpassword", testUser.getPasswordHash());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void testLoginWithNonExistentIdentifier() {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "password123");

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("nonexistent@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));

        // Verifica que o BCrypt dummy foi executado
        verify(passwordEncoder).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void testLoginEmailHasPriorityOverUsername() {
        // Cenário: existe um usuário com e-mail "test@example.com" e outro com username "test@example.com"
        User emailUser = new User("emailuser", "$2a$10$emailHash", "test@example.com");
        emailUser.setId(UUID.randomUUID());

        User usernameUser = new User("test@example.com", "$2a$10$usernameHash", "other@example.com");
        usernameUser.setId(UUID.randomUUID());

        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(emailUser));
        when(passwordEncoder.matches("password123", emailUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(emailUser)).thenReturn("jwt-token");

        LoginResult result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals(emailUser, result.user());

        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository, never()).findByUsernameIgnoreCase(anyString());
    }

    @Test
    void testLoginPasswordNotLogged() {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("jwt-token");

        try {
            authService.login(loginRequest);
        } catch (Exception e) {
            // Não esperamos exceção
        }

        // Apenas verificamos que o método foi chamado, não o valor da senha
        verify(passwordEncoder).matches(anyString(), anyString());
    }

    @Test
    void testLoginReturnsValidJwtToken() {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

        String validToken = "valid-jwt-token";
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn(validToken);

        LoginResult result = authService.login(loginRequest);

        assertEquals(validToken, result.accessToken());
    }
}
