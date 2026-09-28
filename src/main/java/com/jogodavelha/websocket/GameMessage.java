package com.jogodavelha.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para mensagens de jogo via STOMP WebSocket.
 * Representa as mensagens trocadas entre servidor e clientes em tempo real.
 *
 * Esta classe é serializada automaticamente pelo Spring MessageConverter
 * quando enviada através de canais STOMP.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameMessage {

    /**
     * Tipo da mensagem (ex: "MOVE", "GAME_UPDATE", "PLAYER_JOINED", "GAME_OVER")
     */
    private String type;

    /**
     * ID do jogo ao qual esta mensagem se refere
     */
    private String gameId;

    /**
     * ID do jogador que enviou ou é destinatário da mensagem
     */
    private String playerId;

    /**
     * Conteúdo da mensagem (pode ser um objeto complexo como BoardState, Move, etc.)
     */
    private Object payload;

    /**
     * Timestamp da mensagem (opcional, para ordenação temporal)
     */
    private Long timestamp;

    public GameMessage(String type, String gameId, String playerId, Object payload) {
        this.type = type;
        this.gameId = gameId;
        this.playerId = playerId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }
}