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
import com.jogodavelha.telemetry.ActionProfiler;
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
        long start = System.nanoTime();
        try {
            if (event.state() == null) return;
            String roomCode = roomCode(event.state(), event.gameId());
            if (roomCode == null) return;
            var payload = Map.<String, Object>of(
                    "round", event.state().getRoundNumber(),
                    "board", RoomSnapshotService.boardFor(event.state()));
            send(roomCode, "BOARD_UPDATE", payload);
        } finally {
            ActionProfiler.add("ws.boardUpdate", start);
        }
    }

    @EventListener
    public void onSkillResolved(SkillResolvedEvent event) {
        long start = System.nanoTime();
        try {
            GameState state = event.state() != null ? event.state()
                    : states.find(event.gameId()).orElse(null);
            String roomCode = roomCode(state, event.gameId());
            if (state == null || roomCode == null) return;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("by", slot(state, event.playerId()));
            payload.put("skillCode", event.skillCode());
            if (event.targetPosition() != null) payload.put("targetPosition", event.targetPosition());
            if (event.destinationPosition() != null) payload.put("destinationPosition", event.destinationPosition());
            payload.put("absorbed", event.result() == SkillResult.BLOCKED_BY_FORCE_FIELD);
            payload.put("noEffect", event.result() == SkillResult.NO_VALID_TARGET);
            send(roomCode, "ABILITY_RESOLVED", payload);
        } finally {
            ActionProfiler.add("ws.skillResolved", start);
        }
    }

    @EventListener
    public void onTurnTimedOut(TurnTimedOutEvent event) {
        long start = System.nanoTime();
        try {
            GameState state = event.state() != null ? event.state()
                    : states.find(event.gameId()).orElse(null);
            String roomCode = roomCode(state, event.gameId());
            if (state == null || roomCode == null) return;
            send(roomCode, "TURN_TIMEOUT", Map.of(
                    "player", slot(state, event.playerId()),
                    "consecutiveTimeouts", event.consecutiveTimeouts()));
        } finally {
            ActionProfiler.add("ws.turnTimeout", start);
        }
    }

    @EventListener
    @Order(2)
    public void onTurnChanged(TurnChangedEvent event) {
        long start = System.nanoTime();
        try {
            GameState state = event.state() != null ? event.state()
                    : states.find(event.gameId()).orElse(null);
            if (state == null) return;

            String roomCode;
            if (state.getRoomCode() != null) {
                // O estado só existe enquanto a partida está ativa: a remoção no fim da
                // partida e a checagem de fase abaixo substituem a consulta de status.
                roomCode = state.getRoomCode();
            } else {
                // Estados antigos no Redis não têm roomCode: mantém a rota pelo PostgreSQL.
                long dbStart = System.nanoTime();
                try {
                    var route = games.findSnapshotRouteById(event.gameId()).orElse(null);
                    if (route == null || route.getStatus() != GameStatus.IN_PROGRESS) return;
                    roomCode = route.getRoomCode();
                } finally {
                    ActionProfiler.add("db.routeLookup", dbStart);
                }
            }
            if (state.getPhase() != GamePhase.PLAYING || state.getTurnDeadlineEpochMillis() <= 0) return;

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
            send(roomCode, "TURN_START", payload);
        } finally {
            ActionProfiler.add("ws.turnStart", start);
        }
    }

    /**
     * Código da sala sem ir ao PostgreSQL quando o estado já o carrega; estados antigos
     * (sem roomCode) caem na consulta de rota.
     */
    private String roomCode(GameState state, UUID gameId) {
        if (state != null && state.getRoomCode() != null) {
            return state.getRoomCode();
        }
        long dbStart = System.nanoTime();
        try {
            return games.findSnapshotRouteById(gameId).map(route -> route.getRoomCode()).orElse(null);
        } finally {
            ActionProfiler.add("db.routeLookup", dbStart);
        }
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
