package com.jogodavelha.game;

import com.jogodavelha.game.logic.GameState;

import java.util.Map;

/** Contract envelope for room topic events described by contrato-integracao.md. */
public record MatchLifecycleEvent(String type, Map<String, Object> payload, long serverTime) {
    public static MatchLifecycleEvent of(String type, Map<String, Object> payload) {
        return new MatchLifecycleEvent(type, payload, System.currentTimeMillis());
    }

    public static MatchLifecycleEvent roundStart(GameState state, Score score) {
        return of("PHASE_CHANGE", Map.of("gameId", state.getGameId(), "phase", "PLAYING",
                "round", state.getRoundNumber(), "score", score,
                "firstPlayerId", state.getCurrentTurnPlayerId(), "board", state.getBoard(),
                "suddenDeath", state.isSuddenDeath(), "turnDeadline", state.getTurnDeadlineEpochMillis()));
    }
}
