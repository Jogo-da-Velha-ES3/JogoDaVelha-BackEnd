package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;

final class OffensiveEffectSupport {
    private OffensiveEffectSupport() { }

    static boolean isPosition(Integer position) {
        return position != null && position >= 0 && position < GameState.BOARD_SIZE;
    }

    static boolean hasOpponentPiece(GameState state, SkillContext context) {
        char opponent = state.getPlayerSymbol(context.casterId()) == 'X' ? 'O' : 'X';
        return isPosition(context.targetPosition())
                && state.getBoard().charAt(context.targetPosition()) == opponent;
    }

    static boolean hasUnreinforcedOpponentPiece(GameState state, SkillContext context) {
        return hasOpponentPiece(state, context)
                && state.getReinforcedPositions().getOrDefault(context.targetPosition(), 0) <= 0;
    }

    static boolean isEmpty(GameState state, Integer position) {
        return isPosition(position) && state.getBoard().charAt(position) == '-';
    }

    static void removePiece(GameState state, int position) {
        char[] board = state.getBoard().toCharArray();
        board[position] = '-';
        state.setBoard(new String(board));
        state.getFrozenPositions().remove(position);
        state.getReinforcedPositions().remove(position);
    }

    static void movePiece(GameState state, int source, int destination) {
        char[] board = state.getBoard().toCharArray();
        board[destination] = board[source];
        board[source] = '-';
        state.setBoard(new String(board));
        Integer frozenTurns = state.getFrozenPositions().remove(source);
        if (frozenTurns != null) {
            state.getFrozenPositions().put(destination, frozenTurns);
        }
        state.getReinforcedPositions().remove(source);
    }
}
