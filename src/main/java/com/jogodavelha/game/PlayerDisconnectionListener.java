package com.jogodavelha.game;

import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.room.RematchService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PlayerDisconnectionListener {

    private final PlayerService playerService;
    private final PlayerSessionRegistry playerSessionRegistry;
    private final RematchService rematches;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        PlayerSessionRegistry.Removal removal = playerSessionRegistry.removeAndCheckLast(sessionId);
        PlayerSessionRegistry.Entry entry = removal.entry();

        if (entry != null && removal.lastSessionForPlayer()) {
            playerService.markDisconnected(entry.roomCode(), entry.playerId());
            rematches.onPlayerDisconnected(entry.roomCode(), entry.playerId());
        }
    }
}
