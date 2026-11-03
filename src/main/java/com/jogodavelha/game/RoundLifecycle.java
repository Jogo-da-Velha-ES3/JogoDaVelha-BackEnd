package com.jogodavelha.game;

import java.util.UUID;

/** Entry point for BE-023 gameplay and timeout handlers to complete a round. */
public interface RoundLifecycle {
    void finishRound(UUID gameId, int roundNumber, RoundResult result);
}
