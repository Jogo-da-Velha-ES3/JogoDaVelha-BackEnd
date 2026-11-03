package com.jogodavelha.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token é obrigatório") String token,
        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 6, max = 72, message = "Senha deve ter entre 6 e 72 caracteres") String newPassword,
        @NotBlank(message = "Confirmação é obrigatória") String confirmPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest{}";
    } // nunca logar token/senha
}
