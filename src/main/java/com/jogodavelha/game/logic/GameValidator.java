package com.jogodavelha.game.logic;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.Set;
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
        validateBoard(board);

        for (int[] line : WINNING_LINES) {
            char symbol = winningSymbol(board, line, Set.of());
            if (symbol != '-') {
                return Optional.of(symbol);
            }
        }
        return Optional.empty();
    }

    public boolean isDraw(String board) {
        return isDraw(board, Set.of());
    }

    public Optional<Character> resolveWinner(String board, Set<Integer> frozenPositions, char actor) {
        validateBoard(board);
        if (frozenPositions == null || frozenPositions.stream()
                .anyMatch(position -> position == null || position < 0 || position >= GameState.BOARD_SIZE)) {
            throw new IllegalArgumentException("Informe casas congeladas entre 0 e 15.");
        }
        if (actor != 'X' && actor != 'O') {
            throw new IllegalArgumentException("O jogador que agiu deve usar X ou O.");
        }

        for (char symbol : new char[]{actor, actor == 'X' ? 'O' : 'X'}) {
            for (int[] line : WINNING_LINES) {
                if (winningSymbol(board, line, frozenPositions) == symbol) {
                    return Optional.of(symbol);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<List<Integer>> findWinningLine(String board, char symbol, Set<Integer> frozenPositions) {
        validateBoard(board);
        if (symbol != 'X' && symbol != 'O') {
            throw new IllegalArgumentException("O símbolo vencedor deve ser X ou O.");
        }
        if (frozenPositions == null || frozenPositions.stream()
                .anyMatch(position -> position == null || position < 0 || position >= GameState.BOARD_SIZE)) {
            throw new IllegalArgumentException("Informe casas congeladas entre 0 e 15.");
        }
        for (int[] line : WINNING_LINES) {
            if (winningSymbol(board, line, frozenPositions) == symbol) {
                return Optional.of(java.util.Arrays.stream(line).boxed().toList());
            }
        }
        return Optional.empty();
    }

    public boolean isDraw(String board, Set<Integer> frozenPositions) {
        return resolveWinner(board, frozenPositions, 'X').isEmpty() && board.indexOf('-') < 0;
    }

    private static char winningSymbol(String board, int[] line, Set<Integer> frozenPositions) {
        char symbol = board.charAt(line[0]);
        if (symbol == '-') {
            return '-';
        }
        for (int position : line) {
            if (frozenPositions.contains(position) || board.charAt(position) != symbol) {
                return '-';
            }
        }
        return symbol;
    }

    private static void validateBoard(String board) {
        if (board == null || !VALID_BOARD.matcher(board).matches()) {
            throw new IllegalArgumentException("O tabuleiro deve conter 16 casas com X, O ou -.");
        }
    }
}
