package com.jogodavelha.game.logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

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
    }
}
