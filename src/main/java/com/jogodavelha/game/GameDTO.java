package com.jogodavelha.game;

import java.util.UUID;

/**
 * DTO para representação de dados de uma partida.
 * Utilizado para transferência de dados entre API e clientes.
 */
public record GameDTO(
        UUID id,
        UUID roomId,
        String player1Id,
        String player2Id,
        String board,
        int currentRound,
        int victoriesPlayer1,
        int victoriesPlayer2,
        boolean suddenDeath,
        GameStatus status,
        String winnerId,
        boolean rewardsGranted
) {
}
