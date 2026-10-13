package com.jogodavelha.game.logic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.jogodavelha.game.logic.MoveValidationException.Code.*;
import static org.mockito.Mockito.*;

class MoveValidatorTest {
    private static final long NOW = 100000;
    private final MoveValidator validator = new MoveValidator(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    private final UUID playerId = UUID.randomUUID();
    private final GameState state = GameState.createInitial(UUID.randomUUID(), playerId, 10000);

    @BeforeEach
    void setDeadline() {
        state.setTurnDeadlineEpochMillis(NOW + 10000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 15})
    void acceptsFreePositionWithoutChangingState(int position) {
        long deadline = state.getTurnDeadlineEpochMillis();

        assertDoesNotThrow(() -> validator.validate(state, playerId, position, Set.of(8)));

        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(playerId, state.getCurrentTurnPlayerId());
        assertEquals(deadline, state.getTurnDeadlineEpochMillis());
        assertEquals(0L, state.getVersion());
    }

    @Test
    void acceptsPlayerWhoNowHasTheTurn() {
        UUID nextPlayerId = UUID.randomUUID();
        state.setCurrentTurnPlayerId(nextPlayerId);

        assertDoesNotThrow(() -> validator.validate(state, nextPlayerId, 0, Set.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "O"})
    void rejectsOccupiedPosition(String symbol) {
        state.setBoard(symbol + "---------------");

        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));

        assertEquals(POSITION_OCCUPIED, error.getCode());
        assertEquals("A casa já está ocupada.", error.getMessage());
        assertEquals(symbol + "---------------", state.getBoard());
    }

    @Test
    void rejectsPlayerOutsideTheirTurn() {
        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, UUID.randomUUID(), 0, Set.of()));

        assertEquals(NOT_YOUR_TURN, error.getCode());
        assertEquals("Não é o turno deste jogador.", error.getMessage());
    }

    @Test
    void rejectsStateWithoutCurrentPlayer() {
        state.setCurrentTurnPlayerId(null);

        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));

        assertEquals(NOT_YOUR_TURN, error.getCode());
        assertEquals("Não é o turno deste jogador.", error.getMessage());
    }

    @Test
    void rejectsPositionBlockedByShield() {
        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 7, Set.of(7)));

        assertEquals(POSITION_BLOCKED, error.getCode());
        assertEquals("A casa está bloqueada por Escudo.", error.getMessage());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16})
    void rejectsPositionOutsideBoard(int position) {
        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, position, Set.of()));
        assertEquals(INVALID_POSITION, error.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"---------------", "-----------------", "A---------------"})
    void rejectsInvalidBoard(String board) {
        state.setBoard(board);

        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));
        assertEquals(INVALID_BOARD, error.getCode());
    }

    @Test
    void rejectsMissingArguments() {
        assertEquals(INVALID_ARGUMENTS, assertThrows(MoveValidationException.class,
                () -> validator.validate(null, playerId, 0, Set.of())).getCode());
        assertEquals(INVALID_ARGUMENTS, assertThrows(MoveValidationException.class,
                () -> validator.validate(state, null, 0, Set.of())).getCode());
        assertEquals(INVALID_ARGUMENTS, assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, null)).getCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -10000})
    void rejectsMoveAtOrAfterDeadline(long remainingMillis) {
        state.setTurnDeadlineEpochMillis(NOW + remainingMillis);

        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));

        assertEquals(TURN_EXPIRED, error.getCode());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(playerId, state.getCurrentTurnPlayerId());
        assertEquals(NOW + remainingMillis, state.getTurnDeadlineEpochMillis());
        assertEquals(GamePhase.PLAYING, state.getPhase());
        assertEquals(0L, state.getVersion());
    }

    @Test
    void acceptsMoveJustBeforeDeadline() {
        state.setTurnDeadlineEpochMillis(NOW + 1);

        assertDoesNotThrow(() -> validator.validate(state, playerId, 0, Set.of()));
    }

    @Test
    void usesSuppliedTimestampWithoutReadingClock() {
        Clock clock = mock(Clock.class);
        MoveValidator validator = new MoveValidator(clock);

        assertDoesNotThrow(() -> validator.validate(state, playerId, 0, Set.of(), NOW));
        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of(), NOW + 10000));

        assertEquals(TURN_EXPIRED, error.getCode());
        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT, DRAFT_IN_PROGRESS", "ROUND_FINISHED, ROUND_FINISHED", "FINISHED, GAME_FINISHED"})
    void rejectsMoveOutsideActiveRound(GamePhase phase, MoveValidationException.Code code) {
        state.setPhase(phase);

        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));

        assertEquals(code, error.getCode());
        assertEquals(phase, state.getPhase());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(NOW + 10000, state.getTurnDeadlineEpochMillis());
        assertEquals(playerId, state.getCurrentTurnPlayerId());
        assertEquals(0L, state.getVersion());
    }

    @Test
    void rejectsMissingPhase() {
        state.setPhase(null);

        var error = assertThrows(MoveValidationException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));

        assertEquals(INVALID_PHASE, error.getCode());
    }
}
