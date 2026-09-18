package com.jogodavelha.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para mensagens de jogo via WebSocket.
 * Representa as mensagens trocadas entre servidor e clientes em tempo real.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {
    
    private String type;
    private String gameId;
    private String playerId;
    private Object payload;
}