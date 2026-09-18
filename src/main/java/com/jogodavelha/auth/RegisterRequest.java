package com.jogodavelha.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para requisição de registro de novo usuário.
 * Contém as informações necessárias para cadastro de um novo usuário no sistema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    @NotBlank
    private String username;
    
    @NotBlank
    private String password;
    
    @Email
    @NotBlank
    private String email;
}