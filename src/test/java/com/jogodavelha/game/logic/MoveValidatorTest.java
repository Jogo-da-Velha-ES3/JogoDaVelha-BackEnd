package com.jogodavelha.game.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MoveValidatorTest {
    private final MoveValidator validator = new MoveValidator();
    private final UUID playerId = UUID.randomUUID();
    private final GameState state = GameState.createInitial(UUID.randomUUID(), playerId, 10000);

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

        var error = assertThrows(IllegalStateException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));

        assertEquals("A casa já está ocupada.", error.getMessage());
        assertEquals(symbol + "---------------", state.getBoard());
    }

    @Test
    void rejectsPlayerOutsideTheirTurn() {
        var error = assertThrows(IllegalStateException.class,
                () -> validator.validate(state, UUID.randomUUID(), 0, Set.of()));

        assertEquals("Não é o turno deste jogador.", error.getMessage());
    }

    @Test
    void rejectsStateWithoutCurrentPlayer() {
        state.setCurrentTurnPlayerId(null);

        assertThrows(IllegalStateException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));
    }

    @Test
    void rejectsPositionBlockedByShield() {
        var error = assertThrows(IllegalStateException.class,
                () -> validator.validate(state, playerId, 7, Set.of(7)));

        assertEquals("A casa está bloqueada por Escudo.", error.getMessage());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16})
    void rejectsPositionOutsideBoard(int position) {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(state, playerId, position, Set.of()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"---------------", "-----------------", "A---------------"})
    void rejectsInvalidBoard(String board) {
        state.setBoard(board);

        assertThrows(IllegalStateException.class,
                () -> validator.validate(state, playerId, 0, Set.of()));
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(null, playerId, 0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(state, null, 0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(state, playerId, 0, null));
    }
}
