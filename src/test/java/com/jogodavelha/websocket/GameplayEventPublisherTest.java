package com.jogodavelha.websocket;

import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.GameplayBoardUpdatedEvent;
import com.jogodavelha.game.MatchLifecycleEvent;
import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.TurnTimedOutEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.skill.SkillResolvedEvent;
import com.jogodavelha.skill.SkillResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameplayEventPublisherTest {
    private static final long SERVER_TIME = 123_456L;
    private final GameRepository games = mock(GameRepository.class);
    private final GameStateManager states = mock(GameStateManager.class);
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final GameplayEventPublisher publisher = new GameplayEventPublisher(games, states, websocket,
            Clock.fixed(Instant.ofEpochMilli(SERVER_TIME), ZoneOffset.UTC));
    private final GameRepository.SnapshotRoute route = mock(GameRepository.SnapshotRoute.class);

    @BeforeEach
    void setUp() {
        when(route.getRoomCode()).thenReturn("0042");
        when(route.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(games.findSnapshotRouteById(gameId)).thenReturn(java.util.Optional.of(route));
    }

    @Test
    void publishesTurnStartEnvelopeWithSlotsTimeoutCountersAndSilence() {
        GameState state = state(GamePhase.PLAYING, "----------------");
        state.setCurrentTurnPlayerId(p2);
        state.setTurnDurationMillis(10_000L);
        state.setTurnDeadlineEpochMillis(15_000L);
        state.recordTimeout(p1);
        state.recordTimeout(p1);
        state.getSilencedTurns().put(p2, 1);
        when(states.find(gameId)).thenReturn(java.util.Optional.of(state));

        publisher.onTurnChanged(new TurnChangedEvent(gameId, p2, 15_000L, 1, false,
                false, null, null));

        MatchLifecycleEvent event = sent("TURN_START");
        assertEquals(123_456L, event.serverTime());
        assertEquals("P2", event.payload().get("player"));
        assertEquals(5_000L, event.payload().get("startedAt"));
        assertEquals(10L, event.payload().get("durationSeconds"));
        assertEquals(java.util.Map.of("P1", 2, "P2", 0), event.payload().get("consecutiveTimeouts"));
        assertEquals(java.util.Map.of("P1", false, "P2", true), event.payload().get("silenced"));
    }

    @Test
    void publishesSixteenCellBoardWithSlotOwnershipAndActiveCellEffects() {
        GameState state = state(GamePhase.PLAYING, "X--------------O");
        state.getShieldedPositions().put(0, new GameState.ShieldEffect(p1, 2));
        state.getFrozenPositions().put(15, 1);

        publisher.onBoardUpdated(new GameplayBoardUpdatedEvent(gameId, state));

        MatchLifecycleEvent event = sent("BOARD_UPDATE");
        assertEquals(16, ((java.util.List<?>) event.payload().get("board")).size());
        var board = (java.util.List<?>) event.payload().get("board");
        var firstCell = (RoomSnapshotService.Cell) board.getFirst();
        var lastCell = (RoomSnapshotService.Cell) board.getLast();
        assertEquals(RoomSnapshotService.Slot.P1, firstCell.owner());
        assertEquals(new RoomSnapshotService.CellEffect("ESCUDO", 2), firstCell.effects().getFirst());
        assertEquals(RoomSnapshotService.Slot.P2, lastCell.owner());
        assertEquals(new RoomSnapshotService.CellEffect("CONGELAR", 1), lastCell.effects().getFirst());
    }

    @Test
    void publishesAbilityResolutionForEitherPlayerWithOutcomeFlags() {
        when(states.find(gameId)).thenReturn(java.util.Optional.of(state(GamePhase.PLAYING, "----------------")));

        publisher.onSkillResolved(new SkillResolvedEvent(gameId, p2, "TORNADO", 5, 10,
                SkillResult.BLOCKED_BY_FORCE_FIELD));

        MatchLifecycleEvent event = sent("ABILITY_RESOLVED");
        assertEquals("P2", event.payload().get("by"));
        assertEquals("TORNADO", event.payload().get("skillCode"));
        assertEquals(5, event.payload().get("targetPosition"));
        assertEquals(10, event.payload().get("destinationPosition"));
        assertEquals(true, event.payload().get("absorbed"));
        assertEquals(false, event.payload().get("noEffect"));
    }

    @Test
    void publishesAbilityResolutionWithoutEffectForPlayerOne() {
        when(states.find(gameId)).thenReturn(java.util.Optional.of(state(GamePhase.PLAYING, "----------------")));

        publisher.onSkillResolved(new SkillResolvedEvent(gameId, p1, "BOMBA", 7, null,
                SkillResult.NO_VALID_TARGET));

        MatchLifecycleEvent event = sent("ABILITY_RESOLVED");
        assertEquals("P1", event.payload().get("by"));
        assertEquals(7, event.payload().get("targetPosition"));
        assertFalse(event.payload().containsKey("destinationPosition"));
        assertEquals(false, event.payload().get("absorbed"));
        assertEquals(true, event.payload().get("noEffect"));
    }

    @Test
    void publishesTimeoutForTheCorrectRoomAndPlayerSlot() {
        when(states.find(gameId)).thenReturn(java.util.Optional.of(state(GamePhase.PLAYING, "----------------")));

        publisher.onTurnTimedOut(new TurnTimedOutEvent(gameId, p1, 3));

        MatchLifecycleEvent event = sent("TURN_TIMEOUT");
        assertEquals("P1", event.payload().get("player"));
        assertEquals(3, event.payload().get("consecutiveTimeouts"));
    }

    private MatchLifecycleEvent sent(String type) {
        var captor = org.mockito.ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), captor.capture());
        MatchLifecycleEvent event = captor.getValue();
        assertEquals(type, event.type());
        assertEquals(SERVER_TIME, event.serverTime());
        return event;
    }

    private GameState state(GamePhase phase, String board) {
        return GameState.builder().gameId(gameId).board(board).player1Id(p1).player2Id(p2)
                .firstPlayerId(p1).currentTurnPlayerId(p1).roundNumber(1).phase(phase)
                .turnDurationMillis(10_000L).turnDeadlineEpochMillis(15_000L).build();
    }
}
