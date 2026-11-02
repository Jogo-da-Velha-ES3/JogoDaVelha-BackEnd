package com.jogodavelha.game.draft;

import com.jogodavelha.room.PlayerJoinedRoomEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RoomDraftInitializer {
    private final DraftService drafts;

    public RoomDraftInitializer(DraftService drafts) {
        this.drafts = drafts;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(PlayerJoinedRoomEvent event) {
        drafts.start(event.roomId(), event.roomCode(), event.host().id(), event.guest().id());
    }
}
