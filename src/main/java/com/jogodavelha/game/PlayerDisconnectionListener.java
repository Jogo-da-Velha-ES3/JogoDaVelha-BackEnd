package com.jogodavelha.game;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PlayerDisconnectionListener {
    private final PlayerConnectionService connections;
    private final com.jogodavelha.websocket.PlayerPresenceEventPublisher events;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        connections.disconnect(event.getSessionId()).ifPresent(events::disconnected);
    }
}
