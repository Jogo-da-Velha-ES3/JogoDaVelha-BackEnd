package com.jogodavelha.game.draw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class RoundSkillDrawController {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String ability, String abilityToRemove) { }

    private final RoundSkillDrawService draws;

    public RoundSkillDrawController(RoundSkillDrawService draws) {
        this.draws = draws;
    }

    @MessageMapping("/rooms/{code}/draw/pick")
    public void pick(@DestinationVariable String code, Request request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Identidade STOMP inválida.", exception);
        }
        if (request == null) throw new IllegalArgumentException("Informe a habilidade sorteada.");
        draws.pick(code, playerId, request.ability(), request.abilityToRemove());
    }
}
