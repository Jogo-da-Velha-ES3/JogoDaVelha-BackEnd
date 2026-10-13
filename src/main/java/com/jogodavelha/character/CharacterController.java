package com.jogodavelha.character;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/characters")
@RequiredArgsConstructor
@Tag(name = "Character", description = "Inventário de personagens")
public class CharacterController {

    private final CharacterService characterService;

    @Operation(
            summary = "Consultar inventário de personagens",
            description = "Retorna os personagens que o usuário possui"
    )
    @ApiResponse(responseCode = "200", description = "Inventário retornado com sucesso")
    @GetMapping("/inventory")
    public ResponseEntity<List<CharacterDTO>> inventory(
            @Parameter(description = "ID do usuário")
            @RequestParam UUID userId
    ) {
        return ResponseEntity.ok(
                characterService.getInventory(userId)
        );
    }
}
