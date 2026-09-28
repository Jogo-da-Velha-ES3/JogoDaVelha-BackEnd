package com.jogodavelha.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO para representação de dados de uma partida.
 * Utilizado para transferência de dados entre API e clientes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDTO {

    private UUID id;
    private UUID roomId;
    private UUID player1Id;
    private UUID player2Id;
    private String board;
    private int currentRound;
    private int victoriesPlayer1;
    private int victoriesPlayer2;
    private boolean suddenDeath;
    private GameStatus status;
    private UUID winnerId;
    private boolean rewardsGranted;
}