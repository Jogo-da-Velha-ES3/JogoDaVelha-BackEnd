package com.jogodavelha.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para requisição de registro de novo usuário.
 * Contém as informações necessárias para cadastro de um novo usuário no sistema.
 */
public record RegisterRequest(
        @NotBlank
        String username,
        @NotBlank
        String password,
        @Email
        @NotBlank
        String email
) {
}