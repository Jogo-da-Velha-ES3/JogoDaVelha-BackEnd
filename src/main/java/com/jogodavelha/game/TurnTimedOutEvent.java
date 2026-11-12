package com.jogodavelha.game;

import com.jogodavelha.game.logic.GameState;

import java.util.UUID;

public record TurnTimedOutEvent(UUID gameId, UUID playerId, int consecutiveTimeouts, GameState state) {

    public TurnTimedOutEvent(UUID gameId, UUID playerId, int consecutiveTimeouts) {
        this(gameId, playerId, consecutiveTimeouts, null);
    }
}
