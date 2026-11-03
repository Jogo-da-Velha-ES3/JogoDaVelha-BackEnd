package com.jogodavelha.websocket;

import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.GameplayBoardUpdatedEvent;
import com.jogodavelha.game.MatchLifecycleEvent;
import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.TurnTimedOutEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.skill.SkillResolvedEvent;
import com.jogodavelha.skill.SkillResult;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class GameplayEventPublisher {
    private final GameRepository games;
    private final GameStateManager states;
    private final WebSocketService websocket;
    private final Clock clock;

    public GameplayEventPublisher(GameRepository games, GameStateManager states, WebSocketService websocket,
                                  Clock clock) {
        this.games = games;
        this.states = states;
        this.websocket = websocket;
        this.clock = clock;
    }

    @EventListener
    public void onBoardUpdated(GameplayBoardUpdatedEvent event) {
        var route = games.findSnapshotRouteById(event.gameId()).orElse(null);
        if (route == null) return;
        var payload = Map.<String, Object>of(
                "round", event.state().getRoundNumber(),
                "board", RoomSnapshotService.boardFor(event.state()));
        send(route.getRoomCode(), "BOARD_UPDATE", payload);
    }

    @EventListener
    public void onSkillResolved(SkillResolvedEvent event) {
        GameState state = states.find(event.gameId()).orElse(null);
        var route = games.findSnapshotRouteById(event.gameId()).orElse(null);
        if (state == null || route == null) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("by", slot(state, event.playerId()));
        payload.put("skillCode", event.skillCode());
        if (event.targetPosition() != null) payload.put("targetPosition", event.targetPosition());
        if (event.destinationPosition() != null) payload.put("destinationPosition", event.destinationPosition());
        payload.put("absorbed", event.result() == SkillResult.BLOCKED_BY_FORCE_FIELD);
        payload.put("noEffect", event.result() == SkillResult.NO_VALID_TARGET);
        send(route.getRoomCode(), "ABILITY_RESOLVED", payload);
    }

    @EventListener
    public void onTurnTimedOut(TurnTimedOutEvent event) {
        GameState state = states.find(event.gameId()).orElse(null);
        var route = games.findSnapshotRouteById(event.gameId()).orElse(null);
        if (state == null || route == null) return;
        send(route.getRoomCode(), "TURN_TIMEOUT", Map.of(
                "player", slot(state, event.playerId()),
                "consecutiveTimeouts", event.consecutiveTimeouts()));
    }

    @EventListener
    @Order(2)
    public void onTurnChanged(TurnChangedEvent event) {
        var route = games.findSnapshotRouteById(event.gameId()).orElse(null);
        if (route == null || route.getStatus() != GameStatus.IN_PROGRESS) return;
        GameState state = states.find(event.gameId()).orElse(null);
        if (state == null || state.getPhase() != GamePhase.PLAYING || state.getTurnDeadlineEpochMillis() <= 0) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player", slot(state, state.getCurrentTurnPlayerId()));
        payload.put("startedAt", state.getTurnDeadlineEpochMillis() - state.getTurnDurationMillis());
        payload.put("durationSeconds", state.getTurnDurationMillis() / 1000);
        payload.put("consecutiveTimeouts", Map.of(
                "P1", state.getPlayer1ConsecutiveTimeouts(),
                "P2", state.getPlayer2ConsecutiveTimeouts()));
        payload.put("silenced", Map.of(
                "P1", state.getSilencedTurns().getOrDefault(state.getPlayer1Id(), 0) > 0,
                "P2", state.getSilencedTurns().getOrDefault(state.getPlayer2Id(), 0) > 0));
        send(route.getRoomCode(), "TURN_START", payload);
    }

    private void send(String roomCode, String type, Map<String, Object> payload) {
        websocket.sendToTopic("/topic/rooms/" + roomCode,
                new MatchLifecycleEvent(type, payload, clock.millis()));
    }

    private String slot(GameState state, UUID playerId) {
        if (state.getPlayer1Id().equals(playerId)) return "P1";
        if (state.getPlayer2Id().equals(playerId)) return "P2";
        throw new IllegalArgumentException("Jogador não pertence à partida.");
    }
}
