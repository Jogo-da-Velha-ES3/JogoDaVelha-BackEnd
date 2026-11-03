package com.jogodavelha.websocket;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlayerPresenceEventPublisher {
    private final WebSocketService websocket;
    private final RoomRepository rooms;

    public void disconnected(PlayerSessionRegistry.Entry entry) {
        publish("PLAYER_DISCONNECTED", entry);
    }

    public void reconnected(PlayerSessionRegistry.Entry entry) {
        publish("PLAYER_RECONNECTED", entry);
    }

    private void publish(String type, PlayerSessionRegistry.Entry entry) {
        currentOpponentId(entry).ifPresent(opponentId ->
                websocket.sendToUser(opponentId.toString(), "/queue/room",
                        new RoomEventPublisher.EventEnvelope<>(
                                type,
                                new PlayerPresencePayload(entry.roomCode(), entry.slot()),
                                System.currentTimeMillis())));
    }

    private Optional<UUID> currentOpponentId(PlayerSessionRegistry.Entry entry) {
        return rooms.findByCodeAndStatusIn(entry.roomCode(), RoomCodeAllocator.ACTIVE)
                .flatMap(room -> opponentId(room, entry.playerId()));
    }

    private Optional<UUID> opponentId(Room room, UUID playerId) {
        if (room.getPlayer1().getId().equals(playerId)) {
            return Optional.ofNullable(room.getPlayer2()).map(User::getId);
        }
        if (room.getPlayer2() != null && room.getPlayer2().getId().equals(playerId)) {
            return Optional.of(room.getPlayer1().getId());
        }
        return Optional.empty();
    }

    public record PlayerPresencePayload(String roomCode, String slot) {}
}
