package com.jogodavelha.game.service;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.MoveValidator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Executes a move atomically; turn events are published only after Redis commits. */
@Service
public class MoveActionService {
    public record Result(GameState state, Optional<Character> winner) {}

    private final GameStateManager states;
    private final MoveValidator moves;
    private final TurnService turns;
    private final Clock clock;

    public MoveActionService(GameStateManager states, MoveValidator moves, TurnService turns, Clock clock) {
        this.states = states;
        this.moves = moves;
        this.turns = turns;
        this.clock = clock;
    }

    public Result play(UUID gameId, UUID playerId, int position) {
        long now = clock.millis();
        GameState[] committed = new GameState[1];
        @SuppressWarnings("unchecked") Optional<Character>[] winner = new Optional[]{Optional.empty()};
        states.update(gameId, state -> {
            committed[0] = null;
            winner[0] = Optional.empty();
            char symbol;
            try {
                symbol = state.getPlayerSymbol(playerId);
            } catch (IllegalArgumentException exception) {
                throw new AccessDeniedException("Jogador não pertence à partida.", exception);
            }
            moves.validate(state, playerId, position, state.blockedFor(playerId), now);
            StringBuilder board = new StringBuilder(state.getBoard());
            board.setCharAt(position, symbol);
            state.setBoard(board.toString());
            winner[0] = turns.endPlayerAction(state, playerId, now);
            committed[0] = state;
            return state;
        });
        // Must remain outside update: its mutator can be retried after a CAS conflict.
        turns.publishAndSchedule(committed[0], winner[0]);
        return new Result(committed[0], winner[0]);
    }
}
