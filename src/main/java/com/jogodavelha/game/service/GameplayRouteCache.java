package com.jogodavelha.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.room.RoomClosedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Roteamento sala → partida ativa mantido no Redis. Evita as consultas ao PostgreSQL
 * a cada ação STOMP de gameplay. A associação sala/jogadores é imutável enquanto a
 * partida está IN_PROGRESS; qualquer dúvida cai no caminho do banco (ver
 * {@link GameplayRoomResolver}), que também repovoa este cache.
 */
@Component
public class GameplayRouteCache {

    private static final String KEY_PREFIX = "gameplay:route:";

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper;
    private final long ttlMillis;

    public GameplayRouteCache(RedisTemplate<String, Object> redis, ObjectMapper mapper,
                              @Value("${game.state.ttl:1800000}") long ttlMillis) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttlMillis = ttlMillis;
    }

    public record Route(UUID gameId, UUID player1Id, UUID player2Id) {
        public boolean isMember(UUID playerId) {
            return player1Id.equals(playerId) || player2Id != null && player2Id.equals(playerId);
        }
    }

    public void put(String roomCode, UUID gameId, UUID player1Id, UUID player2Id) {
        try {
            redis.opsForValue().set(key(roomCode), mapper.writeValueAsString(
                    new StoredRoute(gameId, player1Id, player2Id)), ttlMillis, TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar a rota da partida.", exception);
        }
    }

    public Optional<Route> find(String roomCode) {
        String json = (String) redis.opsForValue().get(key(roomCode));
        if (json == null) {
            return Optional.empty();
        }
        try {
            StoredRoute stored = mapper.readValue(json, StoredRoute.class);
            return Optional.of(new Route(stored.gameId(), stored.player1Id(), stored.player2Id()));
        } catch (JsonProcessingException exception) {
            evict(roomCode);
            return Optional.empty();
        }
    }

    public void evict(String roomCode) {
        if (roomCode != null) {
            redis.delete(key(roomCode));
        }
    }

    /** Sala encerrada: a rota não pode apontar para uma partida de outra sala reutilizada. */
    @EventListener
    public void onRoomClosed(RoomClosedEvent event) {
        evict(event.roomCode());
    }

    private String key(String roomCode) {
        return KEY_PREFIX + roomCode;
    }

    private record StoredRoute(UUID gameId, UUID player1Id, UUID player2Id) { }
}
