package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.SkillValidationException;

import static com.jogodavelha.game.logic.SkillValidationException.Code.SKILL_INVALID_TARGET;

final class OffensiveEffectSupport {
    private OffensiveEffectSupport() { }

    static boolean isPosition(Integer position) {
        return position != null && position >= 0 && position < GameState.BOARD_SIZE;
    }

    static void validateOpponentPiece(GameState state, SkillContext context) {
        if (context == null || !isPosition(context.targetPosition())
                || state.getBoard().charAt(context.targetPosition())
                != (state.getPlayerSymbol(context.casterId()) == 'X' ? 'O' : 'X')) {
            throw new SkillValidationException(SKILL_INVALID_TARGET,
                    "O alvo deve ser uma peça adversária entre as posições 0 e 15.");
        }
    }

    static boolean hasUnreinforcedOpponentPiece(GameState state, SkillContext context) {
        validateOpponentPiece(state, context);
        return state.getReinforcedPositions().getOrDefault(context.targetPosition(), 0) <= 0;
    }

    static boolean isAvailableDestination(GameState state, SkillContext context, Integer position) {
        return isPosition(position) && state.getBoard().charAt(position) == '-'
                && !state.blockedFor(context.casterId()).contains(position);
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
