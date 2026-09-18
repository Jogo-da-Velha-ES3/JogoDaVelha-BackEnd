package com.jogodavelha.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para representação de dados de uma partida.
 * Utilizado para transferência de dados entre API e clientes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDTO {
    
    private String id;
    private String player1Id;
    private String player2Id;
    private String currentPlayerId;
    private String status;
    private String winnerId;
}