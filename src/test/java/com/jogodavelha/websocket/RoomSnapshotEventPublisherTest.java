package com.jogodavelha.websocket;

import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RoomSnapshotEventPublisherTest {
    private final RoomSnapshotService snapshots = mock(RoomSnapshotService.class);
    private final GameRepository games = mock(GameRepository.class);
    private final GameStateManager states = mock(GameStateManager.class);
    private final RoomSnapshotEventPublisher publisher = new RoomSnapshotEventPublisher(snapshots, games, states);

    @Test
    void publishesAfterTurnEventOnlyWhileMatchRemainsInPlayingPhase() {
        UUID gameId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        var route = mock(GameRepository.SnapshotRoute.class);
        when(route.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(route.getRoomCode()).thenReturn("4242");
        when(games.findSnapshotRouteById(gameId)).thenReturn(Optional.of(route));
        when(states.find(gameId)).thenReturn(Optional.of(GameState.builder().phase(GamePhase.PLAYING).build()));

        publisher.onTurnChanged(event(gameId, playerId));

        verify(snapshots).publish("4242");
    }

    @Test
    void skipsSnapshotWhenLifecycleFinishedMatchOrRoundTransitionIsInProgress() throws Exception {
        UUID gameId = UUID.randomUUID();
        var route = mock(GameRepository.SnapshotRoute.class);
        when(route.getStatus()).thenReturn(GameStatus.FINISHED);
        when(games.findSnapshotRouteById(gameId)).thenReturn(Optional.of(route));

        publisher.onTurnChanged(event(gameId, UUID.randomUUID()));

        verifyNoInteractions(states, snapshots);
    }

    @Test
    void snapshotListenerRunsAfterLifecycleListener() throws Exception {
        Order lifecycleOrder = com.jogodavelha.game.service.GameLifecycleService.class
                .getMethod("onTurnChanged", TurnChangedEvent.class).getAnnotation(Order.class);
        Order snapshotOrder = RoomSnapshotEventPublisher.class
                .getMethod("onTurnChanged", TurnChangedEvent.class).getAnnotation(Order.class);

        assertTrue(lifecycleOrder.value() < snapshotOrder.value());
    }

    private static TurnChangedEvent event(UUID gameId, UUID playerId) {
        return new TurnChangedEvent(gameId, playerId, 1_000, 1, false, false, null, null);
    }

}
