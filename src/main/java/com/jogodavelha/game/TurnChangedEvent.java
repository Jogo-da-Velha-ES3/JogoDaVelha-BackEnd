package com.jogodavelha.game;

import com.jogodavelha.game.logic.GameState;

import java.util.UUID;

/** Publicado após cada troca de turno ou fim de round. A BE-024 (broadcast) e o ciclo da partida escutam. */
public record TurnChangedEvent(
        UUID gameId,
        UUID currentTurnPlayerId,
        long turnDeadlineEpochMillis,
        int roundNumber,
        boolean suddenDeath,
        boolean roundFinished,
        Character winnerSymbol,
        UUID timedOutPlayerId,
        /** Snapshot commitado no Redis; permite aos listeners publicar sem reler o estado. */
        GameState state) {

    public TurnChangedEvent(UUID gameId, UUID currentTurnPlayerId, long turnDeadlineEpochMillis,
                            int roundNumber, boolean suddenDeath, boolean roundFinished,
                            Character winnerSymbol, UUID timedOutPlayerId) {
        this(gameId, currentTurnPlayerId, turnDeadlineEpochMillis, roundNumber, suddenDeath,
                roundFinished, winnerSymbol, timedOutPlayerId, null);
    }
}
