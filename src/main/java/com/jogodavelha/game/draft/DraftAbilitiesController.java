package com.jogodavelha.game.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
public class DraftAbilitiesController {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(List<String> abilities) { }

    private final DraftAbilitiesService abilities;

    public DraftAbilitiesController(DraftAbilitiesService abilities) {
        this.abilities = abilities;
    }

    @MessageMapping("/rooms/{code}/draft/abilities")
    public void confirm(@DestinationVariable String code, @Payload Request request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Identidade STOMP inválida.", exception);
        }
        if (request == null) throw new IllegalArgumentException("Informe as duas habilidades iniciais.");
        abilities.confirm(code, playerId, request.abilities());
    }
}
