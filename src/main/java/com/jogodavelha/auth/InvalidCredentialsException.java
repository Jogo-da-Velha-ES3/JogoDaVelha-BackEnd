package com.jogodavelha.auth;

/**
 * Exceção lançada quando as credenciais fornecidas são inválidas.
 * <p>
 * Esta exceção indica que o e-mail/username ou senha estão incorretos.
 * Na BE-006, esta exceção será mapeada para HTTP 401 (Unauthorized).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException() {
        super("Credenciais inválidas");
    }
}
