package com.jogodavelha.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import com.jogodavelha.error.ApiError;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer JWT";

    @Bean
    public OpenAPI jogoDaVelhaOpenAPI() {
        ResolvedSchema apiErrorSchema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
        Map<String, Schema> schemas = new HashMap<>(apiErrorSchema.referencedSchemas);
        schemas.put("ApiError", apiErrorSchema.schema);
        return new OpenAPI()
                .info(new Info()
                        .title("Jogo da Velha — API")
                        .description("API backend para jogo multiplayer por turnos")
                        .version("1.0.0")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .schemas(schemas)
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Autenticação JWT. Insira o token no formato: Bearer {token}")));
    }
}
