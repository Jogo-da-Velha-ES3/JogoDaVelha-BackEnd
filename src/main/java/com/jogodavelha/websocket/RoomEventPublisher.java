package com.jogodavelha.websocket;

import com.jogodavelha.room.PlayerJoinedRoomEvent;
import com.jogodavelha.room.RoomClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RoomEventPublisher {
    private final WebSocketService webSocketService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(PlayerJoinedRoomEvent event) {
        webSocketService.sendToTopic(
                "/topic/rooms/" + event.roomCode(),
                new EventEnvelope<>(
                        "PLAYER_JOINED",
                        new RoomJoinedPayload(
                                event.roomCode(),
                                event.guestId().toString(),
                                event.guestUsername(),
                                event.hostId().toString()
                        ),
                        Instant.now()
                )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomClosed(RoomClosedEvent event) {
        webSocketService.sendToTopic(
                "/topic/rooms/" + event.roomCode(),
                new EventEnvelope<>(
                        "ROOM_CLOSED",
                        new RoomClosedPayload(
                                event.roomCode(),
                                event.closedBy() == null ? null : event.closedBy().toString(),
                                event.reason()
                        ),
                        Instant.now()
                )
        );
    }

    public record EventEnvelope<T>(
            String type,
            T payload,
            Instant serverTime
    ) {}

    public record RoomJoinedPayload(
            String roomCode,
            String guestId,
            String opponentName,
            String hostId
    ) {}

    public record RoomClosedPayload(
            String roomCode,
            String closedBy,
            String reason
    ) {}

}
