package com.jogodavelha.game;

import com.jogodavelha.game.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PlayerDisconnectionListener {

    private final PlayerService playerService;
    private final PlayerSessionRegistry playerSessionRegistry;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        PlayerSessionRegistry.Entry entry = playerSessionRegistry.remove(sessionId);

        if (entry != null) {
            playerService.markDisconnected(entry.roomCode(), entry.playerId());
        }
    }
}
