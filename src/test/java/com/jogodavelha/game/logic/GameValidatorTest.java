package com.jogodavelha.game.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameValidatorTest {
    private final GameValidator validator = new GameValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "XXXX------------", "----XXXX--------", "--------XXXX----", "------------XXXX",
            "X---X---X---X---", "-X---X---X---X--", "--X---X---X---X-", "---X---X---X---X",
            "X----X----X----X", "---X--X--X--X---"
    })
    void detectsEveryWinningLineForBothSymbols(String board) {
        for (char symbol : new char[]{'X', 'O'}) {
            String winningBoard = board.replace('X', symbol);

            assertEquals(Optional.of(symbol), validator.findWinner(winningBoard));
            assertEquals(Optional.of(symbol), validator.resolveWinner(winningBoard, Set.of(), symbol));
            for (int position = 0; position < GameState.BOARD_SIZE; position++) {
                if (winningBoard.charAt(position) == symbol) {
                    assertTrue(validator.resolveWinner(winningBoard, Set.of(position), symbol).isEmpty());
                }
            }
            assertFalse(validator.isDraw(winningBoard));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "----------------", "XXX-------------", "---XXXX---------",
            "X---X---O---X---", "X----X----X-----"
    })
    void unfinishedBoardHasNeitherWinnerNorDraw(String board) {
        assertTrue(validator.findWinner(board).isEmpty());
        assertFalse(validator.isDraw(board));
    }

    @Test
    void detectsDrawOnFullBoardWithoutWinner() {
        String board = "XXOOOOXXXXOOOOXX";

        assertTrue(validator.findWinner(board).isEmpty());
        assertTrue(validator.isDraw(board));
        assertTrue(validator.isDraw(board, Set.of(0, 15)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XXXXOOXOOOXXXOOO", "OOOOXXOXXXOOOXXX"})
    void fullWinningBoardIsNotDraw(String board) {
        assertEquals(Optional.of(board.charAt(0)), validator.findWinner(board));
        assertFalse(validator.isDraw(board));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"---------------", "-----------------", "A---------------"})
    void rejectsInvalidBoard(String board) {
        assertThrows(IllegalArgumentException.class, () -> validator.findWinner(board));
        assertThrows(IllegalArgumentException.class, () -> validator.isDraw(board));
        assertThrows(IllegalArgumentException.class, () -> validator.resolveWinner(board, Set.of(), 'X'));
        assertThrows(IllegalArgumentException.class, () -> validator.isDraw(board, Set.of()));
    }

    @ParameterizedTest
    @ValueSource(chars = {'X', 'O'})
    void prioritizesActorWhenBothHaveWinningLines(char actor) {
        assertEquals(Optional.of(actor), validator.resolveWinner("XXXXOOOO--------", Set.of(), actor));
    }

    @Test
    void detectsOpponentsWinWhenActorHasNone() {
        assertEquals(Optional.of('O'), validator.resolveWinner("OOOO------------", Set.of(), 'X'));
    }

    @Test
    void frozenPieceOutsideWinningLineDoesNotCancelWin() {
        assertEquals(Optional.of('X'), validator.resolveWinner("XXXXO-----------", Set.of(4), 'X'));
    }

    @Test
    void unchangedLineWinsWhenFreezeExpiresEvenWithoutNewMove() {
        String board = "XXXX------------";
        Set<Integer> frozen = new HashSet<>(Set.of(1));

        assertTrue(validator.resolveWinner(board, frozen, 'O').isEmpty());
        assertFalse(validator.isDraw(board, frozen));

        frozen.clear();

        assertEquals(Optional.of('X'), validator.resolveWinner(board, frozen, 'O'));
        assertFalse(validator.isDraw(board, frozen));
    }

    @Test
    void opponentWinsWhileOtherLineIsFrozen() {
        assertEquals(Optional.of('O'), validator.resolveWinner("XXXXOOOO--------", Set.of(1), 'X'));
    }

    @Test
    void undoneLineDoesNotWinAfterFreezeExpires() {
        assertTrue(validator.resolveWinner("XXXX------------", Set.of(1), 'O').isEmpty());
        assertTrue(validator.resolveWinner("X-XX------------", Set.of(), 'O').isEmpty());
    }

    @Test
    void fullBoardWithoutUnfrozenWinnerIsDraw() {
        String board = "XXXXOOXOOOXXXOOO";

        assertTrue(validator.resolveWinner(board, Set.of(0), 'X').isEmpty());
        assertTrue(validator.isDraw(board, Set.of(0)));
        assertEquals(Optional.of('X'), validator.resolveWinner(board, Set.of(), 'X'));
        assertFalse(validator.isDraw(board, Set.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16})
    void rejectsFrozenPositionOutsideBoard(int position) {
        assertThrows(IllegalArgumentException.class,
                () -> validator.resolveWinner(GameState.EMPTY_BOARD, Set.of(position), 'X'));
        assertThrows(IllegalArgumentException.class,
                () -> validator.isDraw(GameState.EMPTY_BOARD, Set.of(position)));
    }

    @Test
    void rejectsMissingFrozenPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.resolveWinner(GameState.EMPTY_BOARD, null, 'X'));
        Set<Integer> frozen = new HashSet<>();
        frozen.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> validator.resolveWinner(GameState.EMPTY_BOARD, frozen, 'X'));
        assertThrows(IllegalArgumentException.class,
                () -> validator.isDraw(GameState.EMPTY_BOARD, null));
    }

    @ParameterizedTest
    @ValueSource(chars = {'-', 'A', 'x', 'o'})
    void rejectsInvalidActor(char actor) {
        assertThrows(IllegalArgumentException.class,
                () -> validator.resolveWinner(GameState.EMPTY_BOARD, Set.of(), actor));
    }
}
