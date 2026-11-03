package com.jogodavelha.game;

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
        UUID timedOutPlayerId) {

}