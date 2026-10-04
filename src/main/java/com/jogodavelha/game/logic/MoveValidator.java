package com.jogodavelha.game.logic;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class MoveValidator {
    private static final Pattern VALID_BOARD = Pattern.compile("[XO-]{" + GameState.BOARD_SIZE + "}");

    public void validate(GameState state, UUID playerId, int position, Set<Integer> blockedPositions) {
        if (state == null || playerId == null || blockedPositions == null) {
            throw new IllegalArgumentException("Informe o estado, o jogador e as casas bloqueadas.");
        }
        if (position < 0 || position >= GameState.BOARD_SIZE) {
            throw new IllegalArgumentException("A posição deve estar entre 0 e 15.");
        }

        String board = state.getBoard();
        if (board == null || !VALID_BOARD.matcher(board).matches()) {
            throw new IllegalStateException("O tabuleiro deve conter 16 casas com X, O ou -.");
        }
        if (state.getCurrentTurnPlayerId() == null || !playerId.equals(state.getCurrentTurnPlayerId())) {
            throw new IllegalStateException("Não é o turno deste jogador.");
        }
        if (board.charAt(position) != '-') {
            throw new IllegalStateException("A casa já está ocupada.");
        }
        if (blockedPositions.contains(position)) {
            throw new IllegalStateException("A casa está bloqueada por Escudo.");
        }
    }
}
