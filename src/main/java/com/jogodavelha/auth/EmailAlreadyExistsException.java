package com.jogodavelha.auth;

/**
 * Exceção lançada quando se tenta registrar um usuário com um e-mail que já existe.
 * <p>
 * Mapeada para HTTP 409 (Conflict) pelo AuthExceptionHandler.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException() {
        super("E-mail já está em uso");
    }
}
