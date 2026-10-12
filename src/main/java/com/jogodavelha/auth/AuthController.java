package com.jogodavelha.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsável por endpoints de autenticação.
 * Gerencia login, cadastro e operações relacionadas à autenticação de usuários.
 */
@RestController
@RequestMapping("/auth") // BE-044 pode ajustar o prefixo depois
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Cadastro e login de usuários")
public class AuthController {
    
    // Endpoints de autenticação serão implementados aqui
    // Exemplo: login, registro, refresh token, etc.

    private final AuthService authService;

    @Operation(summary = "Cadastrar usuário", description = "Criar um novo usuário")
    @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "Username ou email já estão em uso")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(user));
    }

    @Operation(summary = "Login", description = "Autentica por email ou username e retorna JWT")
    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(LoginResponse.from(authService.login(request)));
    }

}