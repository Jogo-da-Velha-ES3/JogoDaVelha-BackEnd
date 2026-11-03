package com.jogodavelha.game;

import java.util.UUID;

public record TurnTimedOutEvent(UUID gameId, UUID playerId, int consecutiveTimeouts) { }
