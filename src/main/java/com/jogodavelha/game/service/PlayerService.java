package com.jogodavelha.game.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service responsável pelo gerenciamento de jogadores.
 * Controla entrada, saída e estado dos jogadores nas partidas.
 */
@Service
public class PlayerService {
    
    // Lógica de gerenciamento de jogadores será implementada aqui
    // Exemplo: gerenciar conexão, estado de jogador, etc.

    private static final String KEY_PREFIX = "presence:room:";
    private static final String CONNECTED = "CONNECTED";
    private static final String DISCONNECTED = "DISCONNECTED";

    private final RedisTemplate<String, Object> redis;

    public PlayerService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void markConnected(String roomCode, UUID playerId) {
        redis.opsForValue().set(key(roomCode, playerId), CONNECTED);
    }

    public void markDisconnected(String roomCode, UUID playerId) {
        redis.opsForValue().set(key(roomCode, playerId), DISCONNECTED);
    }

    public boolean isConnected(String roomCode, UUID playerId) {
        return CONNECTED.equals(redis.opsForValue().get(key(roomCode, playerId)));
    }

    private String key(String roomCode, UUID playerId) {
        return KEY_PREFIX + roomCode + ":player:" + playerId.toString();
    }
}