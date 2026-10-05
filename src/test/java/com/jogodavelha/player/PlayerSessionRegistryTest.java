package com.jogodavelha.player;

import com.jogodavelha.game.PlayerSessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionRegistryTest {

    private final PlayerSessionRegistry registry = new PlayerSessionRegistry();

    @Test
    void registersAndRemovesSession() {
        UUID playerId = UUID.randomUUID();
        registry.register("session-1", "0042", playerId);

        PlayerSessionRegistry.Entry removed = registry.remove("session-1");

        assertEquals("0042", removed.roomCode());
        assertEquals(playerId, removed.playerId());
        assertNull(registry.remove("session-1")); // já foi removido
    }

    @Test
    void returnsNullForUnknownSession() {
        assertNull(registry.remove("nao-existe"));
    }
}