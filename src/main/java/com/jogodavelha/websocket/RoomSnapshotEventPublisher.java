package com.jogodavelha.websocket;

import com.jogodavelha.game.draft.DraftUpdated;
import com.jogodavelha.room.PlayerJoinedRoomEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomSnapshotEventPublisher {
    private final RoomSnapshotService snapshots;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(PlayerJoinedRoomEvent event) {
        snapshots.publish(event.roomCode());
    }

    @EventListener
    public void onDraftUpdated(DraftUpdated event) {
        snapshots.publish(event.roomCode());
    }
}
