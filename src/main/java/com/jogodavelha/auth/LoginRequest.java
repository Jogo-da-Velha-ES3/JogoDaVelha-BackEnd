package com.jogodavelha.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para requisição de login.
 * Contém as credenciais necessárias para autenticação de um usuário.
 */
public record LoginRequest(
        @Email
        @NotBlank
        String email,
        @NotBlank
        String username,
        @NotBlank
        String password
) {
}