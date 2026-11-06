 package com.jogodavelha.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jogodavelha.game.service.GameplayRoomResolver;
import com.jogodavelha.game.service.RoomLeaveService;
import com.jogodavelha.game.service.MoveActionService;
import com.jogodavelha.skill.SkillActivationService;
import com.jogodavelha.skill.SkillContext;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/** STOMP transport endpoints for authenticated gameplay actions. */
@Controller
@RequiredArgsConstructor
public class GameplayController {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MoveRequest(Integer position) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SkillRequest(String skillCode, Integer targetPosition, Integer destinationPosition) {}

    private final MoveActionService moves;
    private final SkillActivationService skills;
    private final GameplayRoomResolver games;
    private final RoomLeaveService leaves;

    @MessageMapping("/rooms/{code}/move")
    public void move(@DestinationVariable String code, MoveRequest request, Principal principal) {
        UUID actor = authenticatedUser(principal);
        if (request == null || request.position() == null) {
            throw new IllegalArgumentException("Informe a posição da jogada.");
        }
        moves.play(games.resolveActiveGame(code, actor), actor, request.position());
    }

    @MessageMapping("/rooms/{code}/ability")
    public void activateSkill(@DestinationVariable String code, SkillRequest request, Principal principal) {
        UUID actor = authenticatedUser(principal);
        if (request == null || request.skillCode() == null || request.skillCode().isBlank()) {
            throw new IllegalArgumentException("Informe o código da habilidade.");
        }
        skills.activate(games.resolveActiveGame(code, actor), request.skillCode(),
                new SkillContext(actor, request.targetPosition(), request.destinationPosition()));
    }

    /** Sair da sala em qualquer fase; o servidor decide o efeito pela fase (ver RoomLeaveService). */
    @MessageMapping("/rooms/{code}/leave")
    public void leave(@DestinationVariable String code, Principal principal) {
        leaves.leave(code, authenticatedUser(principal));
    }

    private UUID authenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Identidade STOMP inválida.", exception);
        }
    }
}
