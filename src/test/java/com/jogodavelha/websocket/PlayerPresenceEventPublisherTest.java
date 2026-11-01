package com.jogodavelha.websocket;

import com.jogodavelha.game.PlayerSessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerPresenceEventPublisherTest {
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final PlayerPresenceEventPublisher publisher = new PlayerPresenceEventPublisher(websocket);

    @Test
    void disconnectEventUsesEnvelopeAndPrivateOpponentQueueWithP1Slot() {
        UUID disconnectedPlayer = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", disconnectedPlayer, "P1", opponent);

        publisher.disconnected(entry);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToUser(eq(opponent.toString()), eq("/queue/room"), captor.capture());
        var envelope = (RoomEventPublisher.EventEnvelope<?>) captor.getValue();
        assertEquals("PLAYER_DISCONNECTED", envelope.type());
        assertTrue(envelope.serverTime() > 0);
        assertEquals(new PlayerPresenceEventPublisher.PlayerPresencePayload("0042", "P1"), envelope.payload());
    }

    @Test
    void reconnectEventUsesEnvelopeAndPrivateOpponentQueueWithP2Slot() {
        UUID reconnectedPlayer = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", reconnectedPlayer, "P2", opponent);

        publisher.reconnected(entry);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToUser(eq(opponent.toString()), eq("/queue/room"), captor.capture());
        var envelope = (RoomEventPublisher.EventEnvelope<?>) captor.getValue();
        assertEquals("PLAYER_RECONNECTED", envelope.type());
        assertTrue(envelope.serverTime() > 0);
        assertEquals(new PlayerPresenceEventPublisher.PlayerPresencePayload("0042", "P2"), envelope.payload());
    }

    @Test
    void skipsRoomEventsWhenThereIsNoOpponent() {
        publisher.disconnected(new PlayerSessionRegistry.Entry("0042", UUID.randomUUID(), "P1", null));

        verifyNoInteractions(websocket);
    }
}
