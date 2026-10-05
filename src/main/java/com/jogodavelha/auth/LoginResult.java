package com.jogodavelha.auth;

/**
 * Resultado de uma operação de login bem-sucedida.
 * <p>
 * Contém o token JWT de acesso e a entidade User autenticada.
 * O AuthController da BE-006 montará o DTO HTTP a partir deste record.
 */
public record LoginResult(String accessToken, User user) {
}
