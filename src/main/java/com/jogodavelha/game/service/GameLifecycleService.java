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
    private static final long ROUND_PRESENTATION_MILLIS = 2_500;
    private final Set<RoundKey> startedRounds = ConcurrentHashMap.newKeySet();
    private final Set<RoundKey> roundStartsPublished = ConcurrentHashMap.newKeySet();
    private final Map<RoundKey, ScheduledFuture<?>> presentationTimers = new ConcurrentHashMap<>();
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
    @Qualifier("turnTaskScheduler")
    private final TaskScheduler scheduler;

    @EventListener
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
                states.create(game.getId(), game.getPlayer1().getId(), game.getPlayer2().getId(),
                        GameState.DEFAULT_TURN_DURATION_MILLIS);
            } catch (IllegalStateException duplicateState) {
                state = states.find(game.getId()).orElseThrow(() -> duplicateState);
            }
            state = states.find(game.getId()).orElseThrow();
        }
        states.update(game.getId(), current -> {
            current.setInitialSkills(game.getPlayer1().getId(), player1.initialSkills());
            current.setInitialSkills(game.getPlayer2().getId(), player2.initialSkills());
            return current;
        });
        if (room.getStatus() == RoomStatus.WAITING) {
            room.setStatus(RoomStatus.IN_GAME);
            rooms.save(room);
        }
        state = states.find(game.getId()).orElseThrow();
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
            finishMatch(before); // Retry notifications/Redis cleanup after a prior post-commit failure.
            return;
        }
        if (before.getCurrentRound() != roundNumber) {
            return;
        }
        int oldRound = before.getCurrentRound();
        turns.cancel(gameId);
        cancelPresentationTimers(gameId);
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

        if (game.getStatus() == GameStatus.FINISHED) {
            finishMatch(game);
            return;
        }
        long duration = game.getCurrentRound() == Game.MAX_ROUNDS
                ? GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS : GameState.DEFAULT_TURN_DURATION_MILLIS;
        long now = clock.millis();
        states.update(gameId, current -> {
            current.startRound(game.getCurrentRound(), duration, now);
            current.setPhase(GamePhase.DRAFT);
            return current;
        });
        GameState state = states.find(gameId).orElseThrow();
        beginRound(game, state);
    }

    /** Adapter from the existing turn/gameplay event into the lifecycle boundary. */
    @EventListener
    @Order(0)
    public void onTurnChanged(TurnChangedEvent event) {
        if (event.timedOutPlayerId() != null) {
            Game game = gameRepository.findById(event.gameId()).orElse(null);
            if (game != null && game.getStatus() == GameStatus.FINISHED) {
                finishMatch(game);
                return;
            }
            GameState state = states.find(event.gameId()).orElse(null);
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
            if (round == 1) {
                phase(game.getRoom(), "VERSUS", round);
            }
            states.update(game.getId(), current -> {
                if (current.getRoundNumber() == round) {
                    current.setPhase(GamePhase.DRAFT);
                }
                return current;
            });
            GameState roundState = states.find(game.getId()).orElseThrow();
            websocket.sendToTopic(topic(game.getRoom()), MatchLifecycleEvent.of("ROUND_START", Map.of(
                    "round", round,
                    "firstPlayer", slot(roundState, roundState.getCurrentTurnPlayerId()),
                    "suddenDeath", roundState.isSuddenDeath())));
            draw.beginRound(game.getId(), state.getRoundNumber(), game.getPlayer1().getId(),
                    game.getPlayer2().getId());
        } catch (RuntimeException | Error failure) {
            roundStartsPublished.remove(key);
            throw failure;
        }
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onRoundDrawCompleted(RoundDrawCompleted event) {
        startTurnAfterDraft(event.gameId(), event.round());
    }

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
        states.update(gameId, current -> {
            if (current.getPhase() == GamePhase.DRAFT && current.getRoundNumber() == expectedRound) {
                current.setPhase(GamePhase.PLAYING);
                current.setTurnDurationMillis(duration);
                current.setTurnDeadlineEpochMillis(0);
            }
            return current;
        });
        state = states.find(gameId).orElse(null);
        if (state == null || state.getPhase() != GamePhase.PLAYING || state.getTurnDeadlineEpochMillis() > 0) {
            return;
        }
        events.publishEvent(new GameplayBoardUpdatedEvent(gameId, state));
        phase(game.getRoom(), "PLAYING", expectedRound);
        Instant firstTurnAt = Instant.ofEpochMilli(clock.millis() + ROUND_PRESENTATION_MILLIS);
        ScheduledFuture<?> timer = scheduler.schedule(() -> startFirstTurn(gameId, expectedRound), firstTurnAt);
        presentationTimers.put(roundKey, timer);
    }

    private void startFirstTurn(UUID gameId, int expectedRound) {
        RoundKey key = new RoundKey(gameId, expectedRound);
        presentationTimers.remove(key);
        GameState before = states.find(gameId).orElse(null);
        if (before == null || before.getPhase() != GamePhase.PLAYING || before.getRoundNumber() != expectedRound
                || before.getTurnDeadlineEpochMillis() != 0) {
            return;
        }
        long startedAt = clock.millis();
        states.update(gameId, current -> {
            if (current.getPhase() == GamePhase.PLAYING && current.getRoundNumber() == expectedRound
                    && current.getTurnDeadlineEpochMillis() == 0) {
                current.setTurnDeadlineEpochMillis(startedAt + current.getTurnDurationMillis());
            }
            return current;
        });
        GameState state = states.find(gameId).orElse(null);
        if (state != null && state.getPhase() == GamePhase.PLAYING && state.getRoundNumber() == expectedRound
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
            phase(room, "RESULT", game.getCurrentRound());
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
            websocket.sendToTopic(topic(room), MatchLifecycleEvent.of("MATCH_END", result));
            events.publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));
        }
        states.delete(game.getId());
        cancelPresentationTimers(game.getId());
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

    private void cancelPresentationTimers(UUID gameId) {
        presentationTimers.forEach((key, timer) -> {
            if (key.gameId().equals(gameId) && presentationTimers.remove(key, timer)) timer.cancel(false);
        });
    }

    private record RoundKey(UUID gameId, int round) { }
}
