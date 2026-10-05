package com.jogodavelha.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para requisição de login.
 * Contém as credenciais necessárias para autenticação de um usuário.
 * <p>
 * O campo identifier pode ser e-mail ou username.
 */
public record LoginRequest(
        @NotBlank(message = "Identifier é obrigatório")
        @Size(max = 254, message = "Identifier deve ter no máximo 254 caracteres")
        String identifier,
        @NotBlank(message = "Senha é obrigatória")
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest{identifier='" + identifier + "'}";
    }
}