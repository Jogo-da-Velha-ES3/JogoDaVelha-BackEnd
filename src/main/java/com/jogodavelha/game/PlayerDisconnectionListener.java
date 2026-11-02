package com.jogodavelha.game;

import com.jogodavelha.room.RematchService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PlayerDisconnectionListener {
    private final PlayerConnectionService connections;
    private final com.jogodavelha.websocket.PlayerPresenceEventPublisher events;
    private final RematchService rematches;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        connections.disconnect(event.getSessionId()).ifPresent(entry -> {
            events.disconnected(entry);
            rematches.onPlayerDisconnected(entry.roomCode(), entry.playerId());
        });
    }
}
