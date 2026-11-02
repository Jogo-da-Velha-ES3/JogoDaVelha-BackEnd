package com.jogodavelha.game.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class DraftCharacterController {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String characterId) { }

    private final DraftCharacterService characters;

    public DraftCharacterController(DraftCharacterService characters) {
        this.characters = characters;
    }

    @MessageMapping("/rooms/{code}/draft/character")
    public void select(@DestinationVariable String code, @Payload Request request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Identidade STOMP inválida.", exception);
        }
        if (request == null) throw new IllegalArgumentException("Informe o personagem.");
        characters.select(code, playerId, request.characterId());
    }
}
