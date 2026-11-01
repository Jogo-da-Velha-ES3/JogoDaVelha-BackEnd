package com.jogodavelha.auth;

public class PasswordMismatchException extends RuntimeException {
    public PasswordMismatchException() {
        super("As senhas não conferem");
    }
}
