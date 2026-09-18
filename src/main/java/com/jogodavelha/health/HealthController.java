package com.jogodavelha.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Endpoints de disponibilidade da API")
public class HealthController {

    @GetMapping
    @Operation(
            summary = "Verificar disponibilidade da API",
            description = "Endpoint leve para acordar a aplicação após um cold start e confirmar que ela está pronta."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API disponível e pronta para receber requisições")
    })
    public ResponseEntity<HealthResponse> checkHealth() {
        return ResponseEntity.ok(new HealthResponse("UP", "API disponível"));
    }

    public record HealthResponse(String status, String message) {
    }
}
