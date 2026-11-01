package com.jogodavelha.player;

import com.jogodavelha.game.PlayerConnectionService;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerConnectionServiceTest {
    private final PlayerService players = mock(PlayerService.class);
    private final PlayerSessionRegistry sessions = new PlayerSessionRegistry();
    private final PlayerConnectionService connections = new PlayerConnectionService(players, sessions);

    @Test
    void disconnectsP1OnlyAfterItsLastSessionEndsAndOnlyOnce() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", p1, "P1", p2);
        when(players.markDisconnected("0042", p1)).thenReturn(true);

        assertTrue(connections.connect("p1-session-a", entry).firstPlayerSession());
        assertFalse(connections.connect("p1-session-b", entry).firstPlayerSession());
        assertTrue(connections.disconnect("p1-session-a").isEmpty());
        assertEquals(entry, connections.disconnect("p1-session-b").orElseThrow());
        assertTrue(connections.disconnect("p1-session-b").isEmpty());

        verify(players).markDisconnected("0042", p1);
        verify(players, never()).markDisconnected("0042", p2);
    }

    @Test
    void disconnectsP2AndPublishesItsActualSlotAndOpponent() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", p2, "P2", p1);
        when(players.markDisconnected("0042", p2)).thenReturn(true);

        connections.connect("p2-session", entry);

        assertEquals(entry, connections.disconnect("p2-session").orElseThrow());
        assertTrue(connections.disconnect("p2-session").isEmpty());
        verify(players).markDisconnected("0042", p2);
    }

    @Test
    void reconnectTransitionIsReportedOnlyForFirstSessionAfterDisconnect() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markConnected("0042", playerId)).thenReturn(true, false);

        assertTrue(connections.connect("session-a", entry).reconnected());
        assertFalse(connections.connect("session-b", entry).reconnected());
    }

    @Test
    void refreshesPresenceOncePerActivePlayerAndRoom() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        connections.connect("session-a", entry);
        connections.connect("session-b", entry);

        connections.refreshActivePresence();

        verify(players, times(1)).refreshConnected("0042", playerId);
    }
}
