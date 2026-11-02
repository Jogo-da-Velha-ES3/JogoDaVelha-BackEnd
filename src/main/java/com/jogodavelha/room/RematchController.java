package com.jogodavelha.room;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class RematchController {
    private final RematchService rematches;

    public RematchController(RematchService rematches) {
        this.rematches = rematches;
    }

    @MessageMapping("/rooms/{code}/rematch/request")
    public void request(@DestinationVariable String code, Principal principal) {
        rematches.request(code, principalId(principal));
    }

    @MessageMapping("/rooms/{code}/rematch/accept")
    public void accept(@DestinationVariable String code, Principal principal) {
        rematches.accept(code, principalId(principal));
    }

    @MessageMapping("/rooms/{code}/rematch/decline")
    public void decline(@DestinationVariable String code, Principal principal) {
        rematches.decline(code, principalId(principal));
    }

    @MessageMapping("/rooms/{code}/lobby")
    public void returnToLobby(@DestinationVariable String code, Principal principal) {
        rematches.returnToLobby(code, principalId(principal));
    }

    private UUID principalId(Principal principal) {
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
