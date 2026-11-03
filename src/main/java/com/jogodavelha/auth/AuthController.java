package com.jogodavelha.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    private final PasswordResetService passwordResetService;

    @Operation(summary = "Cadastrar usuário", description = "Criar um novo usuário")
    @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Username ou email já estão em uso", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(user));
    }

    @Operation(
            summary = "Login",
            description = "Autentica por email ou username e retorna JWT. O logout é realizado pelo cliente removendo o token armazenado localmente. O token permanece válido até sua expiração."
    )
    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(LoginResponse.from(authService.login(request)));
    }

    @Operation(summary = "Solicitar recuperação de senha",
            description = "Sempre responde 200, exista ou não o e-mail.")
    @ApiResponse(responseCode = "200", description = "Solicitação recebida")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok(
                Map.of(
                    "message",
                    "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha."
                )
        );
    }

    @Operation(summary = "Redefinir senha")
    @ApiResponse(responseCode = "200", description = "Senha alterada")
    @ApiResponse(responseCode = "400", description = "Token inválido/expirado, senhas diferentes ou dados inválidos")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok(Map.of("message", "Senha alterada com sucesso."));
    }
}
