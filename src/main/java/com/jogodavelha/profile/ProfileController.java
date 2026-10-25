package com.jogodavelha.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Perfil do jogador")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(
            summary = "Consultar perfil",
            description = "Retorna dados do perfil do jogador autenticado"
    )
    @GetMapping
    public ResponseEntity<ProfileResponse> profile(
            Authentication authentication
    ) {

        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(
                profileService.getProfile(userId)
        );
    }
}
