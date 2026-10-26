package com.jogodavelha.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Formato único de erro da API")
public record ApiError(
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "Dados inválidos") String message,
        @Schema(example = "400") int status,
        @Schema(example = "2026-10-15T12:34:56Z") String timestamp,
        @Schema(example = "/api/auth/register") String path,
        @Schema(description = "Presente em erros de validação, null em outros casos")
        Map<String, String> fields,
        @Schema(hidden = true) String error) {

    public static ApiError of(String code, String message, int status, String path) {
        return new ApiError(code, message, status, Instant.now().toString(), path, null, null);
    }

    public static ApiError of(String code, String message, int status, String path, Map<String, String> fields) {
        return new ApiError(code, message, status, Instant.now().toString(), path, fields, null);
    }

    /** Compatibility factory for the STOMP handler already using this shared payload. */
    public static ApiError of(org.springframework.http.HttpStatus status, String message, String path) {
        return new ApiError(status.name(), message, status.value(), Instant.now().toString(), path, null,
                status.getReasonPhrase());
    }
}
