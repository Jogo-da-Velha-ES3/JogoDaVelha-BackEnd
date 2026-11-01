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
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        var registration = registry.register("session-1", entry);
        assertTrue(registration.newSession());
        assertTrue(registration.firstPlayerSession());

        PlayerSessionRegistry.Entry removed = registry.remove("session-1");

        assertEquals(entry, removed);
        assertNull(registry.remove("session-1")); // já foi removido
    }

    @Test
    void keepsPlayerConnectedUntilLastSessionIsRemoved() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P2", UUID.randomUUID());

        assertTrue(registry.register("session-1", entry).firstPlayerSession());
        assertFalse(registry.register("session-2", entry).firstPlayerSession());
        assertFalse(registry.removeWithStatus("session-1").lastSession());
        assertTrue(registry.removeWithStatus("session-2").lastSession());
        assertNull(registry.removeWithStatus("session-2"));
    }

    @Test
    void registeringSameSessionTwiceIsIdempotentAndCannotChangeItsIdentity() {
        var entry = new PlayerSessionRegistry.Entry("0042", UUID.randomUUID(), "P1", UUID.randomUUID());
        registry.register("session-1", entry);

        assertFalse(registry.register("session-1", entry).newSession());
        assertThrows(IllegalStateException.class, () -> registry.register("session-1",
                new PlayerSessionRegistry.Entry("1111", UUID.randomUUID(), "P2", UUID.randomUUID())));
        assertEquals(1, registry.activeEntries().size());
    }

    @Test
    void returnsNullForUnknownSession() {
        assertNull(registry.remove("nao-existe"));
    }
}