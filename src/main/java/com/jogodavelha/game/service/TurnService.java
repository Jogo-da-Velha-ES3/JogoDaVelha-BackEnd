package com.jogodavelha.game.service;

import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameStateNotFoundException;
import com.jogodavelha.skill.SkillEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service responsável pelo controle de turnos.
 * Gerencia a alternância de turnos entre jogadores e controle de tempo.
 */

@Slf4j
@Service
public class TurnService {

    private final GameStateManager stateManager;
    private final SkillEngine skillEngine;
    private final Clock clock;
    private final TaskScheduler scheduler;
    private final ApplicationEventPublisher events;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public TurnService(GameStateManager stateManager, SkillEngine skillEngine, Clock clock,
                       @Qualifier("turnTaskScheduler") TaskScheduler turnTaskScheduler, ApplicationEventPublisher events) {
        this.stateManager = stateManager;
        this.skillEngine = skillEngine;
        this.clock = clock;
        this.scheduler = turnTaskScheduler;
        this.events = events;
    }

    /** Duração base do turno que vai começar (sem considerar a Ampulheta). */
    static long baseDuration(GameState state) {
        return state.isSuddenDeath()
                ? GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS
                : GameState.DEFAULT_TURN_DURATION_MILLIS;
    }

    /**
     * Fim de ação (jogada, habilidade ou timeout): resolve a vitória, diminui contadores e,
     * se o round continua, passa a vez com novo prazo. Puro (sem I/O nem RNG): roda dentro do mutator.
     *
     * @return símbolo vencedor, se houver (a vez não é passada e a fase vira ROUND_FINISHED)
     */
    public Optional<Character> endAction(GameState state, UUID actorId, long nowMillis) {
        Optional<Character> winner = skillEngine.endAction(state, actorId);
        if (winner.isPresent()) {
            state.setPhase(GamePhase.ROUND_FINISHED);
            return winner;
        }
        UUID next = state.getOpponentId(actorId);
        long duration = state.effectiveTurnDuration(next, baseDuration(state));
        state.setCurrentTurnPlayerId(next);
        state.setTurnDurationMillis(duration);
        state.setTurnDeadlineEpochMillis(nowMillis + duration);
        return winner;
    }

    /**
     * Para jogada ou habilidade do jogador (usado pela BE-015): chamar dentro do mutator do update.
     * Zera os timeouts consecutivos de quem agiu e passa a vez.
     */
    public Optional<Character> endPlayerAction(GameState state, UUID actorId, long nowMillis) {
        state.resetTimeouts(actorId);
        return endAction(state, actorId, nowMillis);
    }

    /** Chamar DEPOIS do update bem-sucedido: publica o evento e reagenda (ou cancela) o timer. */
    public void publishAndSchedule(GameState snapshot, Optional<Character> winner) {
        events.publishEvent(toEvent(snapshot, winner, null));
        reschedule(snapshot);
    }

    /** Agenda a expiração do turno cujo prazo é {@code deadline}. */
    public void schedule(UUID gameId, long deadline) {
        ScheduledFuture<?> next = scheduler.schedule(() -> expire(gameId, deadline), Instant.ofEpochMilli(deadline));
        ScheduledFuture<?> previous = timers.put(gameId, next);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void cancel(UUID gameId) {
        ScheduledFuture<?> f = timers.remove(gameId);
        if (f != null) {
            f.cancel(false);
        }
    }

    /** Expiração idempotente: só age se o turno com este prazo ainda for o turno atual. */
    public void expire(UUID gameId, long expectedDeadline) {
        long now = clock.millis();
        if (now < expectedDeadline) {
            schedule(gameId, expectedDeadline); // disparou cedo; reagenda
            return;
        }
        GameState[] snapshot = new GameState[1];
        Optional<Character>[] winner = holder();
        UUID[] timedOut = new UUID[1];
        try {
            stateManager.update(gameId, state -> {
                snapshot[0] = null; // o mutator pode reexecutar em conflito de versão
                if (state.getPhase() != GamePhase.PLAYING
                        || state.getTurnDeadlineEpochMillis() != expectedDeadline) {
                    return state; // turno já resolvido: no-op
                }
                UUID player = state.getCurrentTurnPlayerId();
                state.recordTimeout(player);
                if (state.getConsecutiveTimeouts(player) >= 3) {
                    state.setPhase(GamePhase.FINISHED);
                    winner[0] = Optional.empty();
                } else {
                    winner[0] = endAction(state, player, now);
                }
                timedOut[0] = player;
                snapshot[0] = state;
                return state;
            });
        } catch (GameStateNotFoundException e) {
            cancel(gameId);
            return;
        }
        if (snapshot[0] == null) {
            return;
        }
        try {
            events.publishEvent(toEvent(snapshot[0], winner[0], timedOut[0]));
        } finally {
            reschedule(snapshot[0]);
        }
    }

    private void reschedule(GameState s) {
        if (s.getPhase() == GamePhase.PLAYING) {
            schedule(s.getGameId(), s.getTurnDeadlineEpochMillis());
        } else {
            cancel(s.getGameId());
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Character>[] holder() {
        return new Optional[]{Optional.empty()};
    }

    private static TurnChangedEvent toEvent(GameState s, Optional<Character> winner, UUID timedOut) {
        return new TurnChangedEvent(s.getGameId(), s.getCurrentTurnPlayerId(), s.getTurnDeadlineEpochMillis(),
                s.getRoundNumber(), s.isSuddenDeath(), s.getPhase() == GamePhase.ROUND_FINISHED,
                winner.orElse(null), timedOut);
    }
}
