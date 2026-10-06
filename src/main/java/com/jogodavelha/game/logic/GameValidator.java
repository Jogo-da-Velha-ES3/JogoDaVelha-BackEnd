package com.jogodavelha.game.logic;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class GameValidator {
    private static final Pattern VALID_BOARD = Pattern.compile("[XO-]{" + GameState.BOARD_SIZE + "}");
    private static final int[][] WINNING_LINES = {
            {0, 1, 2, 3}, {4, 5, 6, 7}, {8, 9, 10, 11}, {12, 13, 14, 15},
            {0, 4, 8, 12}, {1, 5, 9, 13}, {2, 6, 10, 14}, {3, 7, 11, 15},
            {0, 5, 10, 15}, {3, 6, 9, 12}
    };

    public Optional<Character> findWinner(String board) {
        if (board == null || !VALID_BOARD.matcher(board).matches()) {
            throw new IllegalArgumentException("O tabuleiro deve conter 16 casas com X, O ou -.");
        }

        for (int[] line : WINNING_LINES) {
            char symbol = board.charAt(line[0]);
            if (symbol != '-'
                    && symbol == board.charAt(line[1])
                    && symbol == board.charAt(line[2])
                    && symbol == board.charAt(line[3])) {
                return Optional.of(symbol);
            }
        }
        return Optional.empty();
    }

    public boolean isDraw(String board) {
        return findWinner(board).isEmpty() && board.indexOf('-') < 0;
    }
}
