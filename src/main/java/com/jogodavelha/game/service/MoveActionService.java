package com.jogodavelha.game.service;

import com.jogodavelha.game.GameplayBoardUpdatedEvent;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.MoveValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Executes a move atomically; turn events are published only after Redis commits. */
@Service
@Slf4j
public class MoveActionService {
    public record Result(GameState state, Optional<Character> winner) {}

    private final GameStateManager states;
    private final MoveValidator moves;
    private final TurnService turns;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public MoveActionService(GameStateManager states, MoveValidator moves, TurnService turns, Clock clock,
                             ApplicationEventPublisher events) {
        this.states = states;
        this.moves = moves;
        this.turns = turns;
        this.clock = clock;
        this.events = events;
    }

    public Result play(UUID gameId, UUID playerId, int position) {
        long now = clock.millis();
        GameState[] committed = new GameState[1];
        @SuppressWarnings("unchecked") Optional<Character>[] winner = new Optional[]{Optional.empty()};
        boolean[] turnTransitioned = new boolean[1];
        states.update(gameId, state -> {
            committed[0] = null;
            winner[0] = Optional.empty();
            turnTransitioned[0] = false;
            char symbol;
            try {
                symbol = state.getPlayerSymbol(playerId);
            } catch (IllegalArgumentException exception) {
                throw new AccessDeniedException("Jogador não pertence à partida.", exception);
            }
            moves.validateTurn(state, playerId, now);
            if (state.isMoveUsedThisTurn()) {
                throw new com.jogodavelha.game.logic.MoveValidationException(
                        com.jogodavelha.game.logic.MoveValidationException.Code.MOVE_ALREADY_USED,
                        "A jogada deste turno já foi utilizada.");
            }
            moves.validate(state, playerId, position, state.blockedFor(playerId), now);
            StringBuilder board = new StringBuilder(state.getBoard());
            board.setCharAt(position, symbol);
            state.setBoard(board.toString());
            state.recordMoveAction();
            winner[0] = turns.endPlayerAction(state, playerId, now);
            turnTransitioned[0] = state.getPhase() != GamePhase.PLAYING
                    || !playerId.equals(state.getCurrentTurnPlayerId());
            committed[0] = state;
            return state;
        });
        // Must remain outside update: its mutator can be retried after a CAS conflict.
        long publishStart = System.nanoTime();
        try {
            try {
                events.publishEvent(new GameplayBoardUpdatedEvent(gameId, committed[0]));
            } catch (RuntimeException failure) {
                log.warn("Falha ao publicar atualização do tabuleiro da partida {} (jogada persistida)", gameId, failure);
            }
            if (turnTransitioned[0]) {
                turns.publishAndSchedule(committed[0], winner[0]);
            }
        } catch (RuntimeException failure) {
            // A gravação já foi confirmada: falha de integração não deve reportar rollback ao cliente.
            log.error("Falha pós-commit ao finalizar turno da partida {}", gameId, failure);
        } finally {
            com.jogodavelha.telemetry.ActionProfiler.add("events.publish", publishStart);
        }
        return new Result(committed[0], winner[0]);
    }
}
