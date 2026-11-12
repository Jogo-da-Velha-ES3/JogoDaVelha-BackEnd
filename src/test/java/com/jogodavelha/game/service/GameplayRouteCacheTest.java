package com.jogodavelha.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.room.RoomClosedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GameplayRouteCacheTest {
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private final GameplayRouteCache cache = new GameplayRouteCache(redis, new ObjectMapper(), 300_000);
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    @Test
    void putStoresTheRouteWithTheConfiguredTtl() throws Exception {
        when(redis.opsForValue()).thenReturn(values);

        cache.put("0042", gameId, p1, p2);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("gameplay:route:0042"), json.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
        var stored = new ObjectMapper().readTree(json.getValue());
        assertEquals(gameId.toString(), stored.get("gameId").asText());
        assertEquals(p1.toString(), stored.get("player1Id").asText());
        assertEquals(p2.toString(), stored.get("player2Id").asText());
    }

    @Test
    void findReturnsTheStoredRoute() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("gameplay:route:0042")).thenReturn(
                "{\"gameId\":\"" + gameId + "\",\"player1Id\":\"" + p1 + "\",\"player2Id\":\"" + p2 + "\"}");

        Optional<GameplayRouteCache.Route> route = cache.find("0042");

        assertTrue(route.isPresent());
        assertEquals(gameId, route.get().gameId());
        assertTrue(route.get().isMember(p1));
        assertTrue(route.get().isMember(p2));
        assertFalse(route.get().isMember(UUID.randomUUID()));
    }

    @Test
    void missingOrCorruptedRouteFallsBackToEmpty() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("gameplay:route:0042")).thenReturn(null, "not-json");

        assertTrue(cache.find("0042").isEmpty());
        assertTrue(cache.find("0042").isEmpty());
        // JSON corrompido é removido para não falhar para sempre.
        verify(redis).delete("gameplay:route:0042");
    }

    @Test
    void evictAndRoomClosedRemoveTheRoute() {
        cache.evict("0042");
        cache.onRoomClosed(new RoomClosedEvent("0043", UUID.randomUUID(), null, "HOST_LEFT"));

        verify(redis).delete("gameplay:route:0042");
        verify(redis).delete("gameplay:route:0043");
    }

    @Test
    void putOverwritesThePreviousAssociationForRematch() {
        when(redis.opsForValue()).thenReturn(values);
        UUID rematchGame = UUID.randomUUID();

        cache.put("0042", gameId, p1, p2);
        cache.put("0042", rematchGame, p1, p2);

        // SET simples na mesma chave: a nova partida substitui a anterior.
        verify(values, times(2)).set(eq("gameplay:route:0042"), anyString(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
    }
}
