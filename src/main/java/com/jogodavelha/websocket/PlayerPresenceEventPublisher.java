package com.jogodavelha.websocket;

import com.jogodavelha.game.PlayerSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerPresenceEventPublisher {
    private final WebSocketService websocket;

    public void disconnected(PlayerSessionRegistry.Entry entry) {
        publish("PLAYER_DISCONNECTED", entry);
    }

    public void reconnected(PlayerSessionRegistry.Entry entry) {
        publish("PLAYER_RECONNECTED", entry);
    }

    private void publish(String type, PlayerSessionRegistry.Entry entry) {
        if (entry.opponentId() == null) {
            return;
        }
        websocket.sendToUser(entry.opponentId().toString(), "/queue/room",
                new RoomEventPublisher.EventEnvelope<>(
                        type,
                        new PlayerPresencePayload(entry.roomCode(), entry.slot()),
                        System.currentTimeMillis()));
    }

    public record PlayerPresencePayload(String roomCode, String slot) {}
}
