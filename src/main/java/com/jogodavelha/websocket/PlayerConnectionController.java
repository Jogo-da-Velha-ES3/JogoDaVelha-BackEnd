package com.jogodavelha.websocket;

import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PlayerConnectionController {

    private final PlayerService playerService;
    private final PlayerSessionRegistry playerSessionRegistry;

    /*
    TODO: substituir o recebimento de playerId no payload pela extração do usuário
     autenticado, quando o módulo de autenticação existir.
    */
    @MessageMapping("/room/{code}/connect")
    public void connect(
            @DestinationVariable String code,
            PlayerConnectRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID playerId = request.playerId();
        String sessionId = headerAccessor.getSessionId();

        playerSessionRegistry.register(sessionId, code, playerId);
        playerService.markConnected(code, playerId);
    }

    public record PlayerConnectRequest(UUID playerId) {}
}
