package com.jogodavelha.websocket;

/**
 * DTO para mensagens de jogo via STOMP WebSocket.
 * Representa as mensagens trocadas entre servidor e clientes em tempo real.
 *
 * Esta classe é serializada automaticamente pelo Spring MessageConverter
 * quando enviada através de canais STOMP.
 */
public record GameMessage(
        /**
         * Tipo da mensagem (ex: "MOVE", "GAME_UPDATE", "PLAYER_JOINED", "GAME_OVER")
         */
        String type,
        /**
         * ID do jogo ao qual esta mensagem se refere
         */
        String gameId,
        /**
         * ID do jogador que enviou ou é destinatário da mensagem
         */
        String playerId,
        /**
         * Conteúdo da mensagem (pode ser um objeto complexo como BoardState, Move, etc.)
         */
        Object payload,
        /**
         * Timestamp da mensagem (opcional, para ordenação temporal)
         */
        Long timestamp
) {
    public GameMessage(String type, String gameId, String playerId, Object payload) {
        this(type, gameId, playerId, payload, System.currentTimeMillis());
    }
}