package com.jogodavelha.skill;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
@Tag(name = "Skill", description = "Inventário e habilidades elegíveis")
public class SkillController {

    private final SkillService skillService;

    @Operation(
            summary = "Consultar inventário de habilidades",
            description = "Retorna as habilidades que o usuário possui"
    )
    @ApiResponse(responseCode = "200", description = "Inventário retornado com sucesso")
    @GetMapping("/inventory")
    public ResponseEntity<List<SkillDTO>> inventory(
            Authentication authentication
    ) {

        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(
                skillService.getInventory(userId)
        );
    }

    @Operation(
            summary = "Consultar habilidades iniciais elegíveis",
            description = "Retorna as habilidades iniciais disponíveis para seleção no Draft"
    )
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping("/eligible")
    public ResponseEntity<List<SkillDTO>> eligible(
            Authentication authentication
    ) {

        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(
                skillService.getEligibleInitialSkills(userId)
        );
    }
}
