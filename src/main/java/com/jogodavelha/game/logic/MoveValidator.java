package com.jogodavelha.game.logic;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.jogodavelha.game.logic.MoveValidationException.Code.*;

@Component
public class MoveValidator {
    private static final Pattern VALID_BOARD = Pattern.compile("[XO-]{" + GameState.BOARD_SIZE + "}");
    private final Clock clock;

    public MoveValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(GameState state, UUID playerId, int position, Set<Integer> blockedPositions) {
        validate(state, playerId, position, blockedPositions, clock.millis());
    }

    public void validate(GameState state, UUID playerId, int position, Set<Integer> blockedPositions,
                         long nowMillis) {
        if (state == null || playerId == null || blockedPositions == null) {
            throw new MoveValidationException(INVALID_ARGUMENTS,
                    "Informe o estado, o jogador e as casas bloqueadas.");
        }
        if (position < 0 || position >= GameState.BOARD_SIZE) {
            throw new MoveValidationException(INVALID_POSITION, "A posição deve estar entre 0 e 15.");
        }

        String board = state.getBoard();
        if (board == null || !VALID_BOARD.matcher(board).matches()) {
            throw new MoveValidationException(INVALID_BOARD, "O tabuleiro deve conter 16 casas com X, O ou -.");
        }
        if (state.getPhase() == null) {
            throw new MoveValidationException(INVALID_PHASE, "Informe a fase da partida.");
        }
        switch (state.getPhase()) {
            case DRAFT -> throw new MoveValidationException(DRAFT_IN_PROGRESS, "O Draft ainda não terminou.");
            case ROUND_FINISHED -> throw new MoveValidationException(ROUND_FINISHED, "O round já terminou.");
            case FINISHED -> throw new MoveValidationException(GAME_FINISHED, "A partida já terminou.");
            case PLAYING -> { }
        }
        if (state.getCurrentTurnPlayerId() == null || !playerId.equals(state.getCurrentTurnPlayerId())) {
            throw new MoveValidationException(NOT_YOUR_TURN, "Não é o turno deste jogador.");
        }
        if (nowMillis >= state.getTurnDeadlineEpochMillis()) {
            throw new MoveValidationException(TURN_EXPIRED, "O prazo deste turno já terminou.");
        }
        if (board.charAt(position) != '-') {
            throw new MoveValidationException(POSITION_OCCUPIED, "A casa já está ocupada.");
        }
        if (blockedPositions.contains(position)) {
            throw new MoveValidationException(POSITION_BLOCKED, "A casa está bloqueada por Escudo.");
        }
    }
}
