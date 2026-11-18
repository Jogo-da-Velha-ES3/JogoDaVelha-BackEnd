package com.jogodavelha.game;

import com.jogodavelha.room.RematchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerDisconnectionListener {
    private final PlayerConnectionService connections;
    private final com.jogodavelha.websocket.PlayerPresenceEventPublisher events;
    private final RematchService rematches;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        connections.disconnect(event.getSessionId(), entry -> {
            try {
                events.disconnected(entry);
            } catch (RuntimeException failure) {
                log.warn("Falha ao publicar evento de desconexão para {} na sala {}: {}",
                        entry.playerId(), entry.roomCode(), failure.toString());
            }
            rematches.onPlayerDisconnected(entry.roomCode(), entry.playerId());
        });
    }
}
