package com.jogodavelha.auth;

/**
 * Exceção lançada quando se tenta registrar um usuário com um username que já existe.
 * <p>
 * Esta exceção indica que o username fornecido já está em uso no sistema.
 * Na BE-006, esta exceção será mapeada para HTTP 409 (Conflict).
 */
public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String message) {
        super(message);
    }

    public UsernameAlreadyExistsException() {
        super("Username já está em uso");
    }
}
