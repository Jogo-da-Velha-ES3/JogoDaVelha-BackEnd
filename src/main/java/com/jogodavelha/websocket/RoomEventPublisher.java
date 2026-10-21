package com.jogodavelha.websocket;

import com.jogodavelha.room.PlayerJoinedRoomEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomEventPublisher {
    private final WebSocketService webSocketService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(PlayerJoinedRoomEvent event) {
        webSocketService.sendToTopic(
                "/topic/rooms/" + event.roomCode(),
                new RoomJoinedMessage(
                        "PLAYER_JOINED",
                        event.roomCode(),
                        event.guestId().toString(),
                        event.guestUsername(),
                        event.hostId().toString()
                )
        );
    }

    public record RoomJoinedMessage(
            String type,
            String roomCode,
            String guestId,
            String opponentName,
            String hostId
    ) {}
}