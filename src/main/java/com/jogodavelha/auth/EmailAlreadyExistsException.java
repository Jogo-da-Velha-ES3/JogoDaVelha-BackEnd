package com.jogodavelha.auth;

/**
 * Exceção lançada quando se tenta registrar um usuário com um e-mail que já existe.
 * <p>
 * Esta exceção indica que o e-mail fornecido já está em uso no sistema.
 * Na BE-006, esta exceção será mapeada para HTTP 409 (Conflict).
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException() {
        super("E-mail já está em uso");
    }
}
