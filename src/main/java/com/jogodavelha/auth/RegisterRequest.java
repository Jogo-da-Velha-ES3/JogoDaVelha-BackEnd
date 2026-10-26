package com.jogodavelha.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para requisição de registro de novo usuário.
 * Contém as informações necessárias para cadastro de um novo usuário no sistema.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegisterRequest(
        @NotBlank(message = "Username é obrigatório")
        @Size(min = 3, max = 15, message = "Username deve ter entre 3 e 15 caracteres")
        String username,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, max = 72, message = "Senha deve ter entre 6 e 72 caracteres")
        String password,

        @NotBlank(message = "E-mail é obrigatório")
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
        @Email(message = "E-mail inválido")
        @Schema(example = "teste@exemplo.com")
        @Pattern(regexp = "^[\\w.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "E-mail deve ter formato válido com domínio e TLD de pelo menos 2 letras")
        String email
) {
    @Override
    public String toString() {
        return "RegisterRequest{username='" + username + "', email='" + email + "'}";
    }
}