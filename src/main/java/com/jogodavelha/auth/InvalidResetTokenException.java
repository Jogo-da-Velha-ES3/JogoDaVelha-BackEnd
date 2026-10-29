package com.jogodavelha.auth;

public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("Token de recuperação inválido ou expirado");
    }
}

