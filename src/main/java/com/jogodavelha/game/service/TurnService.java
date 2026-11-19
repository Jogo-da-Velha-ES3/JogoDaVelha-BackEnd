package com.jogodavelha.game.service;

import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.TurnTimedOutEvent;
import com.jogodavelha.game.GameplayBoardUpdatedEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameStateNotFoundException;
import com.jogodavelha.game.logic.GameValidator;
import com.jogodavelha.game.logic.MoveValidationException;
import com.jogodavelha.skill.SkillEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final ConcurrentHashMap<UUID, ScheduledTurn> timers = new ConcurrentHashMap<>();

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

    /** Resolve vitória/empate após uma ação, sem reduzir efeitos nem alternar o turno. */
    private Optional<Character> resolveActionOutcome(GameState state, UUID actorId) {
        var frozenPositions = state.frozenSet();
        Optional<Character> winner = skillEngine.resolveWinner(state, actorId);
        if (winner.isPresent()) {
            state.setWinningLine(GameValidator.findWinningLine(state.getBoard(), winner.get(), frozenPositions)
                    .orElseThrow(() -> new IllegalStateException("A vitória não possui uma linha válida.")));
            state.setPhase(GamePhase.ROUND_FINISHED);
            return winner;
        }
        if (state.getBoard() != null && state.getBoard().indexOf('-') < 0) {
            state.setWinningLine(java.util.List.of());
            state.setPhase(GamePhase.ROUND_FINISHED);
            return Optional.empty();
        }
        state.setWinningLine(java.util.List.of());
        return winner;
    }

    /** Registra resultado de uma ação; alterna somente quando ambas foram consumidas. */
    public Optional<Character> actionResolved(GameState state, UUID actorId, long nowMillis) {
        state.resetTimeouts(actorId);
        Optional<Character> winner = resolveActionOutcome(state, actorId);
        if (state.getPhase() == GamePhase.PLAYING && state.haveBothActionsThisTurn()) {
            completeTurn(state, actorId, nowMillis);
        }
        return winner;
    }

    private void completeTurn(GameState state, UUID actorId, long nowMillis) {
        state.tickEffects();
        UUID next = state.getOpponentId(actorId);
        long duration = state.effectiveTurnDuration(next, baseDuration(state));
        state.setCurrentTurnPlayerId(next);
        state.setTurnDurationMillis(duration);
        state.setTurnDeadlineEpochMillis(nowMillis + duration);
        state.resetTurnActions();
    }

    /** Para ação válida: zera inatividade e conclui o turno somente se ambas as ações foram usadas. */
    public Optional<Character> endPlayerAction(GameState state, UUID actorId, long nowMillis) {
        return actionResolved(state, actorId, nowMillis);
    }

    /** Encerra manualmente um turno depois de ao menos uma ação válida. */
    public void endTurn(UUID gameId, UUID playerId) {
        long now = clock.millis();
        GameState[] committed = new GameState[1];
        stateManager.update(gameId, state -> {
            committed[0] = null;
            try {
                state.getPlayerSymbol(playerId);
            } catch (IllegalArgumentException exception) {
                throw new org.springframework.security.access.AccessDeniedException("Jogador não pertence à partida.", exception);
            }
            if (!playerId.equals(state.getCurrentTurnPlayerId())) {
                throw new org.springframework.security.access.AccessDeniedException("Não é a vez deste jogador.");
            }
            if (state.getPhase() != GamePhase.PLAYING || now >= state.getTurnDeadlineEpochMillis()) {
                throw new IllegalStateException("O prazo do turno expirou ou o turno não está ativo.");
            }
            if (!state.hasAnyActionThisTurn()) {
                throw new MoveValidationException(MoveValidationException.Code.END_TURN_WITHOUT_ACTION,
                        "Realize uma ação antes de encerrar o turno.");
            }
            Optional<Character> winner = resolveActionOutcome(state, playerId);
            if (state.getPhase() == GamePhase.PLAYING) completeTurn(state, playerId, now);
            committed[0] = state;
            return state;
        });
        if (committed[0] != null) {
            publishSafely(new GameplayBoardUpdatedEvent(gameId, committed[0]), gameId);
            publishAndSchedule(committed[0], Optional.empty());
        }
    }

    /** Chamar DEPOIS do update bem-sucedido: publica o evento e reagenda (ou cancela) o timer. */
    public void publishAndSchedule(GameState snapshot, Optional<Character> winner) {
        try {
            events.publishEvent(toEvent(snapshot, winner, null));
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar evento de turno para a partida {} (estado já persistido)",
                    snapshot.getGameId(), failure);
        } finally {
            rescheduleSafely(snapshot);
        }
    }

    /** Recupera timers a partir do deadline persistido, sem conceder novo prazo. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPlayingTurnTimers() {
        try {
            for (UUID gameId : stateManager.findAllGameIds()) {
                try {
                    GameState state = stateManager.find(gameId).orElse(null);
                    if (state == null) continue;
                    if (state.getPhase() == GamePhase.PLAYING && state.getTurnDeadlineEpochMillis() > 0) {
                        if (clock.millis() >= state.getTurnDeadlineEpochMillis()) {
                            expire(gameId, state.getTurnDeadlineEpochMillis());
                        } else if (!hasLiveTimer(gameId, state.getTurnDeadlineEpochMillis())) {
                            schedule(gameId, state.getTurnDeadlineEpochMillis());
                        }
                    } else if (state.getPhase() == GamePhase.ROUND_FINISHED
                            || (state.getPhase() == GamePhase.FINISHED && hasForfeitTimeout(state))) {
                        cancelSafely(gameId);
                        publishSafely(toRecoveryEvent(state), gameId);
                    } else {
                        cancelSafely(gameId);
                    }
                } catch (RuntimeException failure) {
                    log.error("Falha ao recuperar timer da partida {}", gameId, failure);
                }
            }
        } catch (RuntimeException failure) {
            log.error("Falha ao listar partidas para recuperar timers de turno", failure);
        }
    }

    /** Reconcilia falhas transitórias do scheduler e reprocessa lifecycle terminal após falha de listener. */
    @Scheduled(fixedDelayString = "${game.turn.recovery-interval-ms:1000}")
    public void reconcilePersistedTurnWork() {
        recoverPlayingTurnTimers();
    }

    /** Agenda a expiração do turno cujo prazo é {@code deadline}. */
    public synchronized void schedule(UUID gameId, long deadline) {
        ScheduledTurn existing = timers.get(gameId);
        if (existing != null && existing.deadline() == deadline && !existing.future().isDone()) return;
        ScheduledFuture<?> next = scheduler.schedule(() -> expire(gameId, deadline), Instant.ofEpochMilli(deadline));
        if (next == null) throw new IllegalStateException("O agendador não retornou um timer para a partida " + gameId);
        if (existing != null) {
            try {
                existing.future().cancel(false);
            } catch (RuntimeException cancellationFailure) {
                next.cancel(false);
                throw cancellationFailure;
            }
        }
        ScheduledTurn previous = timers.put(gameId, new ScheduledTurn(deadline, next));
        if (previous != null && previous != existing) previous.future().cancel(false);
    }

    public synchronized void cancel(UUID gameId) {
        ScheduledTurn timer = timers.get(gameId);
        if (timer != null) {
            timer.future().cancel(false);
            timers.remove(gameId, timer);
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
        int[] consecutiveTimeouts = new int[1];
        boolean[] boardChanged = new boolean[1];
        UUID[] expiredPlayer = new UUID[1];
        try {
            stateManager.update(gameId, state -> {
                snapshot[0] = null; // o mutator pode reexecutar em conflito de versão
                if (state.getPhase() != GamePhase.PLAYING
                        || state.getTurnDeadlineEpochMillis() != expectedDeadline) {
                    throw new StaleTurnExpirationException(); // sem gravar uma versão artificial
                }
                UUID player = state.getCurrentTurnPlayerId();
                expiredPlayer[0] = player;
                String previousBoard = state.getBoard();
                var previousShields = new java.util.HashMap<>(state.getShieldedPositions());
                var previousFrozen = new java.util.HashMap<>(state.getFrozenPositions());
                var previousReinforced = new java.util.HashMap<>(state.getReinforcedPositions());
                boolean acted = state.hasAnyActionThisTurn();
                if (!acted) state.recordTimeout(player);
                consecutiveTimeouts[0] = state.getConsecutiveTimeouts(player);
                if (!acted && state.getConsecutiveTimeouts(player) >= 3) {
                    state.setPhase(GamePhase.FINISHED);
                    winner[0] = Optional.empty();
                } else {
                    winner[0] = resolveActionOutcome(state, player);
                    if (state.getPhase() == GamePhase.PLAYING) completeTurn(state, player, now);
                }
                // Uma expiração após ação válida conclui o turno, mas não o classifica como timeout.
                timedOut[0] = acted ? null : player;
                boardChanged[0] = !previousBoard.equals(state.getBoard())
                        || !previousShields.equals(state.getShieldedPositions())
                        || !previousFrozen.equals(state.getFrozenPositions())
                        || !previousReinforced.equals(state.getReinforcedPositions());
                snapshot[0] = state;
                return state;
            });
        } catch (StaleTurnExpirationException stale) {
            return;
        } catch (GameStateNotFoundException e) {
            cancel(gameId);
            return;
        }
        if (snapshot[0] == null) {
            return;
        }
        publishSafely(new TurnTimedOutEvent(gameId, expiredPlayer[0], consecutiveTimeouts[0], snapshot[0]), gameId);
        if (boardChanged[0]) publishSafely(new GameplayBoardUpdatedEvent(gameId, snapshot[0]), gameId);
        publishSafely(toEvent(snapshot[0], winner[0], timedOut[0]), gameId);
        rescheduleSafely(snapshot[0]);
    }

    private void reschedule(GameState s) {
        if (s.getPhase() == GamePhase.PLAYING) {
            schedule(s.getGameId(), s.getTurnDeadlineEpochMillis());
        } else {
            cancel(s.getGameId());
        }
    }

    private void rescheduleSafely(GameState state) {
        try {
            reschedule(state);
        } catch (RuntimeException failure) {
            log.error("Falha ao agendar/cancelar timer da partida {}", state.getGameId(), failure);
        }
    }

    private void cancelSafely(UUID gameId) {
        try {
            cancel(gameId);
        } catch (RuntimeException failure) {
            log.error("Falha ao cancelar timer da partida {}; nova tentativa será feita", gameId, failure);
        }
    }

    private boolean hasLiveTimer(UUID gameId, long deadline) {
        ScheduledTurn timer = timers.get(gameId);
        return timer != null && timer.deadline() == deadline && !timer.future().isDone();
    }

    private static boolean hasForfeitTimeout(GameState state) {
        return state.getConsecutiveTimeouts(state.getCurrentTurnPlayerId()) >= 3;
    }

    private static TurnChangedEvent toRecoveryEvent(GameState state) {
        UUID timedOutPlayer = state.getPhase() == GamePhase.FINISHED
                && hasForfeitTimeout(state) ? state.getCurrentTurnPlayerId() : null;
        Optional<Character> winner = state.getWinningLine() == null || state.getWinningLine().isEmpty()
                ? Optional.empty() : Optional.of(state.getPlayerSymbol(state.getCurrentTurnPlayerId()));
        return toEvent(state, winner, timedOutPlayer);
    }

    private void publishSafely(Object event, UUID gameId) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar evento {} da partida {} (estado já persistido)",
                    event.getClass().getSimpleName(), gameId, failure);
        }
    }

    private static final class StaleTurnExpirationException extends RuntimeException { }

    @SuppressWarnings("unchecked")
    private static Optional<Character>[] holder() {
        return new Optional[]{Optional.empty()};
    }

    private static TurnChangedEvent toEvent(GameState s, Optional<Character> winner, UUID timedOut) {
        return new TurnChangedEvent(s.getGameId(), s.getCurrentTurnPlayerId(), s.getTurnDeadlineEpochMillis(),
                s.getRoundNumber(), s.isSuddenDeath(), s.getPhase() == GamePhase.ROUND_FINISHED,
                winner.orElse(null), timedOut, s);
    }

    private record ScheduledTurn(long deadline, ScheduledFuture<?> future) { }
}
