package com.jogodavelha.game;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade que representa uma partida de Jogo da Velha.
 * Armazena informações persistentes sobre as partidas.
 */
@Entity
@Table(name = "games")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Game {
    
    @Id
    private String id;
    
    private String player1Id;
    private String player2Id;
    private String currentPlayerId;
    private String status;
    private String winnerId;
}