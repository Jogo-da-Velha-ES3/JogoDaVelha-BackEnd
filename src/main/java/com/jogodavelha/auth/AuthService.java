package com.jogodavelha.auth;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Service responsável pela lógica de autenticação.
 * Gerencia validação de credenciais, geração de tokens e operações de autenticação.
 */
@Service
@Validated
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registra um novo usuário no sistema.
     *
     * @param request DTO com os dados de registro
     * @return a entidade User criada (sem expor o passwordHash na resposta na BE-006)
     * @throws UsernameAlreadyExistsException se o username já existe
     * @throws EmailAlreadyExistsException se o e-mail já existe
     * @throws jakarta.validation.ConstraintViolationException se a validação falhar
     */
    @Transactional
    public User register(@Valid RegisterRequest request) {
        String normalizedUsername = request.username().trim();
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UsernameAlreadyExistsException("Username já está em uso");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("E-mail já está em uso");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(normalizedUsername, passwordHash, normalizedEmail);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("uk_users_username")) {
                throw new UsernameAlreadyExistsException("Username já está em uso");
            }
            if (e.getMessage() != null && e.getMessage().contains("uk_users_email")) {
                throw new EmailAlreadyExistsException("E-mail já está em uso");
            }
            throw e;
        }
    }
}