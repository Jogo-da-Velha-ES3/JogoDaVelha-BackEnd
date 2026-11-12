package com.jogodavelha.game.service;

import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameOutcome;
import com.jogodavelha.game.MatchFinishedEvent;
import com.jogodavelha.game.GameNotFoundException;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.MatchLifecycleEvent;
import com.jogodavelha.game.RewardResult;
import com.jogodavelha.game.RoundLifecycle;
import com.jogodavelha.game.RoundResult;
import com.jogodavelha.game.RoundSkillDraft;
import com.jogodavelha.game.RoundUpdate;
import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.draft.DraftCompleted;
import com.jogodavelha.game.draw.RoundDrawCompleted;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import com.jogodavelha.auth.User;
import com.jogodavelha.game.GameplayBoardUpdatedEvent;
import com.jogodavelha.websocket.WebSocketService;
import com.jogodavelha.telemetry.ActionProfiler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Coordinates persistence, Redis round state, turn timers and lifecycle notifications. */
@Service
@RequiredArgsConstructor
public class GameLifecycleService implements RoundLifecycle {
    /** Tela de Confronto (VS) antes do round 1. */
    static final long VERSUS_MILLIS = 3_000;
    /** Anúncio do fim do round antes do próximo round ou do resultado. */
    static final long ROUND_END_MILLIS = 2_500;
    /** Apresentação do round antes do primeiro TURN_START. */
    static final long ROUND_PRESENTATION_MILLIS = 2_500;
    private final Set<RoundKey> startedRounds = ConcurrentHashMap.newKeySet();
    private final Set<RoundKey> roundStartsPublished = ConcurrentHashMap.newKeySet();
    private final Map<LifecyclePauseElapsed, ScheduledFuture<?>> pauses = new ConcurrentHashMap<>();
    private final Set<UUID> matchEndPublished = ConcurrentHashMap.newKeySet();
    private final GameService games;
    private final GameRepository gameRepository;
    private final RoomRepository rooms;
    private final GameStateManager states;
    private final TurnService turns;
    private final WebSocketService websocket;
    private final ObjectProvider<RoundSkillDraft> skillDraft;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final GameplayRouteCache routes;
    @Qualifier("turnTaskScheduler")
    private final TaskScheduler scheduler;

    // As reações do ciclo chegam pela thread do STOMP ou do timer, sem transação, e leem relações
    // LAZY da partida (sala, jogadores). Sem @Transactional: LazyInitializationException.
    @EventListener
    @Transactional
    public void onDraftCompleted(DraftCompleted event) {
        Room room = rooms.findById(event.roomId())
                .orElseThrow(() -> new IllegalStateException("Sala do Draft não encontrada."));
        if (!room.isReadyToStart()) {
            throw new IllegalStateException("Sala não está pronta para iniciar.");
        }
        DraftCompleted.Selection player1 = selectionFor(event, room.getPlayer1().getId());
        DraftCompleted.Selection player2 = selectionFor(event, room.getPlayer2().getId());

        Game game;
        try {
            game = games.startGame(room, player1.characterId(), player2.characterId());
        } catch (DataIntegrityViolationException duplicateStart) {
            return; // The concurrent winner owns initialization and will publish the initial state.
        }
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            return;
        }
        GameState state = states.find(game.getId()).orElse(null);
        boolean stateCreated = state == null;
        if (state == null) {
            try {
                states.create(game.getId(), room.getCode(), game.getPlayer1().getId(), game.getPlayer2().getId(),
                        GameState.DEFAULT_TURN_DURATION_MILLIS);
            } catch (IllegalStateException duplicateState) {
                state = states.find(game.getId()).orElseThrow(() -> duplicateState);
            }
            state = states.find(game.getId()).orElseThrow();
        }
        state = states.update(game.getId(), current -> {
            current.setInitialSkills(game.getPlayer1().getId(), player1.initialSkills());
            current.setInitialSkills(game.getPlayer2().getId(), player2.initialSkills());
            return current;
        });
        routes.put(room.getCode(), game.getId(), game.getPlayer1().getId(), game.getPlayer2().getId());
        if (room.getStatus() == RoomStatus.WAITING) {
            room.setStatus(RoomStatus.IN_GAME);
            rooms.save(room);
        }
        if (stateCreated || state.getPhase() == GamePhase.DRAFT) {
            beginRound(game, state);
        } else if (state.getPhase() == GamePhase.PLAYING && state.getTurnDeadlineEpochMillis() > 0) {
            turns.schedule(game.getId(), state.getTurnDeadlineEpochMillis());
        }
    }

    @Override
    public void finishRound(UUID gameId, int roundNumber, RoundResult result) {
        Game before = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        if (before.getStatus() == GameStatus.FINISHED) {
            // O resultado sai quando a pausa do fim do round acabar; sem pausa pendente, é uma nova
            // tentativa depois de uma falha na publicação.
            if (!hasPendingPause(gameId, LifecyclePauseElapsed.Step.ROUND_END)) finishMatch(before);
            return;
        }
        if (before.getCurrentRound() != roundNumber) {
            return;
        }
        int oldRound = before.getCurrentRound();
        turns.cancel(gameId);
        cancelPauses(gameId);
        RoundUpdate update = games.applyRoundResult(gameId, roundNumber, result);
        if (!update.applied()) {
            return;
        }
        Game game = update.game();
        Room room = game.getRoom();
        GameState completedState = states.find(gameId).orElse(null);
        Map<String, Object> roundEnd = new LinkedHashMap<>();
        roundEnd.put("round", oldRound);
        roundEnd.put("winner", result == RoundResult.DRAW ? null
                : slot(room, result == RoundResult.PLAYER1_WIN ? game.getPlayer1() : game.getPlayer2()));
        if (result != RoundResult.DRAW && completedState != null
                && completedState.getWinningLine() != null && !completedState.getWinningLine().isEmpty()) {
            roundEnd.put("winningLine", List.copyOf(completedState.getWinningLine()));
        }
        roundEnd.put("score", scoreBySlot(game, room));
        websocket.sendToTopic(topic(room), MatchLifecycleEvent.of("ROUND_END", roundEnd));
        phase(room, "ROUND_END", oldRound);
        // O próximo round (ou o resultado) só começa depois do anúncio do fim do round.
        pause(gameId, oldRound, LifecyclePauseElapsed.Step.ROUND_END, ROUND_END_MILLIS);
    }

    /** Fim de uma pausa do ciclo: segue para a próxima etapa, se a partida ainda estiver nela. */
    @EventListener
    @Transactional
    public void onPauseElapsed(LifecyclePauseElapsed pause) {
        pauses.remove(pause);
        switch (pause.step()) {
            case VERSUS -> openRound(pause.gameId(), pause.round());
            case ROUND_END -> continueAfterRoundEnd(pause.gameId(), pause.round());
            case PRESENTATION -> startFirstTurn(pause.gameId(), pause.round());
        }
    }

    private void continueAfterRoundEnd(UUID gameId, int finishedRound) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return;
        if (game.getStatus() == GameStatus.FINISHED) {
            finishMatch(game);
            return;
        }
        GameState finished = states.find(gameId).orElse(null);
        if (finished == null || finished.getRoundNumber() != finishedRound
                || game.getCurrentRound() != finishedRound + 1) {
            return; // A partida já avançou ou foi encerrada durante a pausa.
        }
        long duration = game.getCurrentRound() == Game.MAX_ROUNDS
                ? GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS : GameState.DEFAULT_TURN_DURATION_MILLIS;
        long now = clock.millis();
        GameState state = states.update(gameId, current -> {
            if (current.getRoundNumber() == finishedRound) {
                current.startRound(game.getCurrentRound(), duration, now);
                current.setPhase(GamePhase.DRAFT);
            }
            return current;
        });
        beginRound(game, state);
    }

    /** Adapter from the existing turn/gameplay event into the lifecycle boundary. */
    @EventListener
    @Order(0)
    @Transactional
    public void onTurnChanged(TurnChangedEvent event) {
        long start = System.nanoTime();
        try {
            onTurnChangedInternal(event);
        } finally {
            ActionProfiler.add("lifecycle.turnChanged", start);
        }
    }

    private void onTurnChangedInternal(TurnChangedEvent event) {
        if (event.timedOutPlayerId() != null) {
            Game game = gameRepository.findById(event.gameId()).orElse(null);
            if (game != null && game.getStatus() == GameStatus.FINISHED) {
                finishMatch(game);
                return;
            }
            GameState state = event.state() != null ? event.state() : states.find(event.gameId()).orElse(null);
            if (game != null && state != null && state.getConsecutiveTimeouts(event.timedOutPlayerId()) >= 3
                    && game.getStatus() == GameStatus.IN_PROGRESS) {
                turns.cancel(game.getId());
                try {
                    game = games.forfeit(game.getId(), event.timedOutPlayerId().equals(game.getPlayer1().getId())
                            ? game.getPlayer1() : game.getPlayer2());
                    finishMatch(game);
                } catch (IllegalStateException ignored) {
                    // A concurrent round result or timeout already finalized the match.
                }
                return;
            }
        }
        if (!event.roundFinished()) {
            return;
        }
        RoundResult result = event.winnerSymbol() == null ? RoundResult.DRAW
                : event.winnerSymbol() == 'X' ? RoundResult.PLAYER1_WIN : RoundResult.PLAYER2_WIN;
        finishRound(event.gameId(), event.roundNumber(), result);
    }

    private void beginRound(Game game, GameState state) {
        int round = state.getRoundNumber();
        RoundKey key = new RoundKey(game.getId(), round);
        RoundSkillDraft draw = skillDraft.getIfAvailable();
        if (draw == null) {
            throw new IllegalStateException(
                    "RoundSkillDraft é obrigatório: o sorteio de habilidades do round não está disponível.");
        }
        if (!roundStartsPublished.add(key)) return;
        try {
            states.update(game.getId(), current -> {
                if (current.getRoundNumber() == round) {
                    current.setPhase(GamePhase.DRAFT);
                }
                return current;
            });
            if (round == 1) {
                // O round 1 (e o sorteio de 8s) só abre depois da tela de Confronto.
                phase(game.getRoom(), "VERSUS", round);
                pause(game.getId(), round, LifecyclePauseElapsed.Step.VERSUS, VERSUS_MILLIS);
                return;
            }
            openRound(game, round, draw);
        } catch (RuntimeException | Error failure) {
            roundStartsPublished.remove(key);
            throw failure;
        }
    }

    private void openRound(UUID gameId, int round) {
        Game game = gameRepository.findById(gameId).orElse(null);
        GameState state = states.find(gameId).orElse(null);
        if (game == null || game.getStatus() != GameStatus.IN_PROGRESS || state == null
                || state.getRoundNumber() != round || state.getPhase() != GamePhase.DRAFT) {
            return; // Encerrada (ex: desistência) durante a pausa.
        }
        RoundSkillDraft draw = skillDraft.getIfAvailable();
        if (draw == null) {
            throw new IllegalStateException(
                    "RoundSkillDraft é obrigatório: o sorteio de habilidades do round não está disponível.");
        }
        openRound(game, round, draw);
    }

    /** Anuncia o round e abre o sorteio da habilidade. */
    private void openRound(Game game, int round, RoundSkillDraft draw) {
        GameState roundState = states.find(game.getId()).orElseThrow();
        websocket.sendToTopic(topic(game.getRoom()), MatchLifecycleEvent.of("ROUND_START", Map.of(
                "round", round,
                "firstPlayer", slot(roundState, roundState.getCurrentTurnPlayerId()),
                "suddenDeath", roundState.isSuddenDeath())));
        draw.beginRound(game.getId(), round, game.getPlayer1().getId(), game.getPlayer2().getId());
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onRoundDrawCompleted(RoundDrawCompleted event) {
        startTurnAfterDraft(event.gameId(), event.round());
    }

    @Transactional
    public void forfeitActiveGame(UUID gameId, UUID loserId) {
        Game game = games.forfeit(gameId, loserId);
        finishMatch(game);
    }

    private void startTurnAfterDraft(UUID gameId, int expectedRound) {
        RoundKey roundKey = new RoundKey(gameId, expectedRound);
        if (!startedRounds.add(roundKey)) {
            return;
        }
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null || game.getStatus() == GameStatus.FINISHED) {
            startedRounds.remove(roundKey);
            return;
        }
        GameState state = states.find(gameId).orElse(null);
        if (state == null || state.getPhase() != GamePhase.DRAFT || state.getRoundNumber() != expectedRound) {
            startedRounds.remove(roundKey);
            return;
        }
        long duration = state.isSuddenDeath() ? GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS
                : GameState.DEFAULT_TURN_DURATION_MILLIS;
        GameState updated = states.update(gameId, current -> {
            if (current.getPhase() == GamePhase.DRAFT && current.getRoundNumber() == expectedRound) {
                current.setPhase(GamePhase.PLAYING);
                current.setTurnDurationMillis(duration);
                current.setTurnDeadlineEpochMillis(0);
            }
            return current;
        });
        if (updated.getPhase() != GamePhase.PLAYING || updated.getTurnDeadlineEpochMillis() > 0) {
            return;
        }
        events.publishEvent(new GameplayBoardUpdatedEvent(gameId, updated));
        phase(game.getRoom(), "PLAYING", expectedRound);
        pause(gameId, expectedRound, LifecyclePauseElapsed.Step.PRESENTATION, ROUND_PRESENTATION_MILLIS);
    }

    private void startFirstTurn(UUID gameId, int expectedRound) {
        GameState before = states.find(gameId).orElse(null);
        if (before == null || before.getPhase() != GamePhase.PLAYING || before.getRoundNumber() != expectedRound
                || before.getTurnDeadlineEpochMillis() != 0) {
            return;
        }
        long startedAt = clock.millis();
        GameState state = states.update(gameId, current -> {
            if (current.getPhase() == GamePhase.PLAYING && current.getRoundNumber() == expectedRound
                    && current.getTurnDeadlineEpochMillis() == 0) {
                current.setTurnDeadlineEpochMillis(startedAt + current.getTurnDurationMillis());
            }
            return current;
        });
        if (state.getPhase() == GamePhase.PLAYING && state.getRoundNumber() == expectedRound
                && state.getTurnDeadlineEpochMillis() > 0) {
            turns.publishAndSchedule(state, java.util.Optional.empty());
        }
    }

    private void finishMatch(Game game) {
        turns.cancel(game.getId());
        Room room = rooms.findById(game.getRoom().getId())
                .orElseThrow(() -> new IllegalStateException("Sala da partida não encontrada."));
        // Keep room IN_GAME so BE-039 can reuse it for rematch; CLOSED is for leave/decline flows.
        if (room.getStatus() == RoomStatus.WAITING) {
            room.setStatus(RoomStatus.IN_GAME);
            rooms.save(room);
        }
        boolean firstPublication = matchEndPublished.add(game.getId());
        if (firstPublication) {
            RewardResult reward = game.getRewardResult();
            if (reward == null) {
                matchEndPublished.remove(game.getId());
                throw new IllegalStateException("Resultado de recompensas da partida indisponível.");
            }
            Map<String, Object> outcome = new LinkedHashMap<>();
            if (game.getEndReason() == GameEndReason.FORFEIT_TIMEOUT) {
                outcome.put("kind", "WO");
                outcome.put("winner", slot(room, game.getWinner()));
            } else if (game.getOutcome() == GameOutcome.DRAW) {
                outcome.put("kind", "ABSOLUTE_DRAW");
            } else {
                outcome.put("kind", "WIN");
                outcome.put("winner", slot(room, game.getWinner()));
                outcome.put("score", java.util.List.of(game.getScore().player1(), game.getScore().player2()));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outcome", outcome);
            result.put("coins", Map.of(
                    slot(room, game.getPlayer1()), reward.player1Reward(),
                    slot(room, game.getPlayer2()), reward.player2Reward()));
            result.put("reason", game.getEndReason().name());
            result.put("forfeit", game.getEndReason() == GameEndReason.FORFEIT_TIMEOUT);
            // O resultado chega antes da troca de fase: a tela de Resultado já abre com ele.
            websocket.sendToTopic(topic(room), MatchLifecycleEvent.of("MATCH_END", result));
            phase(room, "RESULT", game.getCurrentRound());
            events.publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));
        }
        states.delete(game.getId());
        routes.evict(room.getCode());
        cancelPauses(game.getId());
    }

    private Map<String, Integer> scoreBySlot(Game game, Room room) {
        return Map.of(slot(room, game.getPlayer1()), game.getScore().player1(),
                slot(room, game.getPlayer2()), game.getScore().player2());
    }

    private String slot(Room room, User player) {
        if (player == null) throw new IllegalStateException("Jogador do resultado indisponível.");
        if (room.getPlayer1().getId().equals(player.getId())) return "P1";
        if (room.getPlayer2() != null && room.getPlayer2().getId().equals(player.getId())) return "P2";
        throw new IllegalStateException("Jogador do resultado não pertence à sala.");
    }

    private static DraftCompleted.Selection selectionFor(DraftCompleted event, UUID playerId) {
        return event.players().stream()
                .filter(selection -> selection.playerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "DraftCompleted não contém a seleção do jogador da sala."));
    }

    private String topic(Room room) {
        return "/topic/rooms/" + room.getCode();
    }

    private void phase(Room room, String name, int round) {
        websocket.sendToTopic(topic(room), MatchLifecycleEvent.of("PHASE_CHANGE",
                Map.of("phase", name, "round", round)));
    }

    private String slot(GameState state, UUID playerId) {
        if (state.getPlayer1Id().equals(playerId)) return "P1";
        if (state.getPlayer2Id().equals(playerId)) return "P2";
        throw new IllegalArgumentException("Jogador não pertence à partida.");
    }

    /** Agenda o fim da pausa; a etapa seguinte roda em {@link #onPauseElapsed}, numa transação. */
    private void pause(UUID gameId, int round, LifecyclePauseElapsed.Step step, long millis) {
        LifecyclePauseElapsed pause = new LifecyclePauseElapsed(gameId, round, step);
        ScheduledFuture<?> timer = scheduler.schedule(() -> events.publishEvent(pause),
                Instant.ofEpochMilli(clock.millis() + millis));
        ScheduledFuture<?> previous = pauses.put(pause, timer);
        if (previous != null) previous.cancel(false);
    }

    private boolean hasPendingPause(UUID gameId, LifecyclePauseElapsed.Step step) {
        return pauses.keySet().stream().anyMatch(pause -> pause.gameId().equals(gameId) && pause.step() == step);
    }

    private void cancelPauses(UUID gameId) {
        pauses.forEach((pause, timer) -> {
            if (pause.gameId().equals(gameId) && pauses.remove(pause, timer)) timer.cancel(false);
        });
    }

    private record RoundKey(UUID gameId, int round) { }
}
