package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;

public final class BoardAdjacency {
    private static final int WIDTH = 4;

    private BoardAdjacency() { }

    public static boolean isAdjacent(int first, int second) {
        if (first < 0 || first >= GameState.BOARD_SIZE || second < 0 || second >= GameState.BOARD_SIZE
                || first == second) {
            return false;
        }
        return Math.abs(first / WIDTH - second / WIDTH) <= 1
                && Math.abs(first % WIDTH - second % WIDTH) <= 1;
    }
}
