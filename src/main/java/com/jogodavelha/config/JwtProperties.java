package com.jogodavelha.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades de configuração para JWT.
 * <p>
 * Mapeia as propriedades jwt.secret e jwt.expiration do application.yml.
 * A aplicação falha ao iniciar se a configuração for inválida (fail fast).
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank(message = "JWT secret é obrigatório")
        @Size(min = 32, message = "JWT secret deve ter no mínimo 32 bytes para HS256")
        String secret,

        @Min(value = 1, message = "JWT expiration deve ser maior que 0")
        Long expiration
) {
}
