package com.jogodavelha.player;

import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private final PlayerService service = new PlayerService(redis);

    private final UUID playerId = UUID.randomUUID();
    private final String roomCode = "0042";

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void marksPlayerAsConnected() {
        service.markConnected(roomCode, playerId);

        verify(valueOps).set("presence:room:0042:player:" + playerId, "CONNECTED");
    }

    @Test
    void marksPlayerAsDisconnected() {
        service.markDisconnected(roomCode, playerId);

        verify(valueOps).set("presence:room:0042:player:" + playerId, "DISCONNECTED");
    }

    @Test
    void reportsConnectedWhenStatusIsConnected() {
        when(valueOps.get("presence:room:0042:player:" + playerId)).thenReturn("CONNECTED");

        assertTrue(service.isConnected(roomCode, playerId));
    }

    @Test
    void reportsNotConnectedWhenStatusIsDisconnectedOrMissing() {
        when(valueOps.get("presence:room:0042:player:" + playerId)).thenReturn("DISCONNECTED");
        assertFalse(service.isConnected(roomCode, playerId));

        when(valueOps.get("presence:room:0042:player:" + playerId)).thenReturn(null);
        assertFalse(service.isConnected(roomCode, playerId));
    }
}