package com.jogodavelha.websocket;

import com.jogodavelha.game.draft.DraftUpdated;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.MatchFinishedEvent;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.PlayerJoinedRoomEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomSnapshotEventPublisher {
    private final RoomSnapshotService snapshots;
    private final GameRepository games;
    private final GameStateManager states;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlayerJoined(PlayerJoinedRoomEvent event) {
        snapshots.publish(event.roomCode());
    }

    @EventListener
    public void onMatchFinished(MatchFinishedEvent event) {
        snapshots.publish(event.roomCode());
    }

    @EventListener
    public void onSnapshotRequested(RoomSnapshotRequestedEvent event) {
        snapshots.publish(event.roomCode());
    }

    @EventListener
    @Order(1)
    public void onDraftUpdated(DraftUpdated event) {
        snapshots.publish(event.roomCode());
    }

    /** Runs after GameLifecycleService (order 0) has resolved timeout, round and match transitions. */
    @EventListener
    @Order(1)
    public void onTurnChanged(TurnChangedEvent event) {
        var route = games.findSnapshotRouteById(event.gameId()).orElse(null);
        if (route == null || route.getStatus() != GameStatus.IN_PROGRESS) return;
        var state = states.find(event.gameId()).orElse(null);
        if (state == null || state.getPhase() != GamePhase.PLAYING) return;
        snapshots.publish(route.getRoomCode());
    }
}
