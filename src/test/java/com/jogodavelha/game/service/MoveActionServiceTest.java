package com.jogodavelha.game.service;

import com.jogodavelha.game.logic.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoveActionServiceTest {
    UUID game = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
    GameState state;
    GameStateManager states;
    TurnService turns;
    MoveActionService service;

    @BeforeEach void setup() {
        state = GameState.createInitial(game, p1, p2, 10_000, new java.security.SecureRandom());
        state.setCurrentTurnPlayerId(p1);
        state.setTurnDeadlineEpochMillis(20_000);
        states = mock(GameStateManager.class);
        turns = mock(TurnService.class);
        doAnswer(invocation -> {
            Function<GameState, GameState> mutator = invocation.getArgument(1);
            mutator.apply(state);
            return null;
        }).when(states).update(eq(game), any());
        when(turns.endPlayerAction(any(), any(), anyLong())).thenReturn(Optional.empty());
        service = new MoveActionService(states, new MoveValidator(Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC)),
                turns, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC));
    }

    @Test void validMoveUpdatesStateAndEndsActionOnce() {
        var result = service.play(game, p1, 0);
        assertEquals('X', state.getBoard().charAt(0));
        assertSame(state, result.state());
        verify(turns).endPlayerAction(state, p1, 1000);
        verify(turns).publishAndSchedule(state, Optional.empty());
    }

    @Test void invalidPositionDoesNotMutateOrPublish() {
        assertThrows(MoveValidationException.class, () -> service.play(game, p1, 16));
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        verify(turns, never()).publishAndSchedule(any(), any());
    }

    @Test void opponentAndTerminalBoardAreHandledByExistingDomainLogic() {
        assertThrows(MoveValidationException.class, () -> service.play(game, p2, 0));
        state.setBoard("XXXX------------");
        when(turns.endPlayerAction(any(), any(), anyLong())).thenReturn(Optional.of('X'));
        var result = service.play(game, p1, 4);
        assertEquals(Optional.of('X'), result.winner());
        verify(turns).publishAndSchedule(state, Optional.of('X'));
    }

    @Test void persistenceFailureDoesNotPublish() {
        doThrow(new ConcurrentStateModificationException(game, 5)).when(states).update(eq(game), any());
        assertThrows(ConcurrentStateModificationException.class, () -> service.play(game, p1, 0));
        verify(turns, never()).publishAndSchedule(any(), any());
    }
}
