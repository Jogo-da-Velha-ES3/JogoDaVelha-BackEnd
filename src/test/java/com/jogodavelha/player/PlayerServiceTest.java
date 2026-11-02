package com.jogodavelha.player;

import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerServiceTest {
    private static final long TTL_MILLIS = 60_000;
    private static final String KEY = "presence:room:0042:player:";

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private final PlayerService service = new PlayerService(redis, TTL_MILLIS);
    private final UUID playerId = UUID.randomUUID();

    @Test
    void marksInitialConnectionWithConfiguredTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY + playerId)).thenReturn(null);

        assertFalse(service.markConnected("0042", playerId));

        verify(valueOps).set(KEY + playerId, "CONNECTED", Duration.ofMillis(TTL_MILLIS));
    }

    @Test
    void reportsReconnectOnlyForDisconnectedPresenceAndRefreshesTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY + playerId)).thenReturn("DISCONNECTED", "CONNECTED");

        assertTrue(service.markConnected("0042", playerId));
        assertFalse(service.markConnected("0042", playerId));

        verify(valueOps, times(2)).set(KEY + playerId, "CONNECTED", Duration.ofMillis(TTL_MILLIS));
    }

    @Test
    void marksDisconnectedOnlyWhenPreviouslyConnectedAndUsesTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY + playerId)).thenReturn("CONNECTED", "DISCONNECTED");

        assertTrue(service.markDisconnected("0042", playerId));
        assertFalse(service.markDisconnected("0042", playerId));

        verify(valueOps).set(KEY + playerId, "DISCONNECTED", Duration.ofMillis(TTL_MILLIS));
    }

    @Test
    void activeSessionRefreshesPresenceLease() {
        when(redis.opsForValue()).thenReturn(valueOps);

        service.refreshConnected("0042", playerId);

        verify(valueOps).set(KEY + playerId, "CONNECTED", Duration.ofMillis(TTL_MILLIS));
    }

    @Test
    void expiredOrDisconnectedPresenceIsNotReportedAsConnected() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY + playerId)).thenReturn("DISCONNECTED");

        assertFalse(service.isConnected("0042", playerId));
        when(valueOps.get(KEY + playerId)).thenReturn((Object) null);
        assertFalse(service.isConnected("0042", playerId));
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerService(redis, 0));
    }
}
