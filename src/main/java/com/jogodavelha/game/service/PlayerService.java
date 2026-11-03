package com.jogodavelha.game.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class PlayerService {

    private static final String KEY_PREFIX = "presence:room:";
    private static final String CONNECTED = "CONNECTED";
    private static final String DISCONNECTED = "DISCONNECTED";

    private final RedisTemplate<String, Object> redis;
    private final long presenceTtlMillis;

    @Autowired
    public PlayerService(RedisTemplate<String, Object> redis,
                         @Value("${game.presence.ttl:300000}") long presenceTtlMillis) {
        if (presenceTtlMillis <= 0) {
            throw new IllegalArgumentException("O TTL de presença deve ser positivo.");
        }
        this.redis = redis;
        this.presenceTtlMillis = presenceTtlMillis;
    }

    public PlayerService(RedisTemplate<String, Object> redis) {
        this(redis, 300_000);
    }

    public synchronized boolean markConnected(String roomCode, UUID playerId) {
        String key = key(roomCode, playerId);
        boolean reconnected = DISCONNECTED.equals(redis.opsForValue().get(key));
        setPresence(key, CONNECTED);
        return reconnected;
    }

    public synchronized boolean markDisconnected(String roomCode, UUID playerId) {
        String key = key(roomCode, playerId);
        if (!CONNECTED.equals(redis.opsForValue().get(key))) {
            return false;
        }
        setPresence(key, DISCONNECTED);
        return true;
    }

    public boolean isConnected(String roomCode, UUID playerId) {
        return CONNECTED.equals(redis.opsForValue().get(key(roomCode, playerId)));
    }

    public synchronized void refreshConnected(String roomCode, UUID playerId) {
        setPresence(key(roomCode, playerId), CONNECTED);
    }

    private void setPresence(String key, String status) {
        redis.opsForValue().set(key, status, Duration.ofMillis(presenceTtlMillis));
    }

    private String key(String roomCode, UUID playerId) {
        return KEY_PREFIX + roomCode + ":player:" + playerId.toString();
    }
}