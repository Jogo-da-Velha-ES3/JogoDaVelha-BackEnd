package com.jogodavelha.websocket;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static java.util.Optional.of;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerPresenceEventPublisherTest {
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final PlayerPresenceEventPublisher publisher = new PlayerPresenceEventPublisher(websocket, rooms);
    private UUID player1Id;
    private UUID player2Id;

    @BeforeEach
    void setUp() {
        player1Id = UUID.randomUUID();
        player2Id = UUID.randomUUID();
        Room room = new Room("0042", user(player1Id, "host"));
        room.join(user(player2Id, "guest"));
        when(rooms.findByCodeAndStatusIn("0042", RoomCodeAllocator.ACTIVE)).thenReturn(of(room));
    }

    @Test
    void disconnectResolvesOpponentFromCurrentRoomWhenSessionHasNoOpponent() {
        var entry = new PlayerSessionRegistry.Entry("0042", player1Id, "P1", null);

        publisher.disconnected(entry);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToUser(eq(player2Id.toString()), eq("/queue/room"), captor.capture());
        var envelope = (RoomEventPublisher.EventEnvelope<?>) captor.getValue();
        assertEquals("PLAYER_DISCONNECTED", envelope.type());
        assertTrue(envelope.serverTime() > 0);
        assertEquals(new PlayerPresenceEventPublisher.PlayerPresencePayload("0042", "P1"), envelope.payload());
    }

    @Test
    void reconnectEventUsesEnvelopeAndPrivateOpponentQueueWithP2Slot() {
        var entry = new PlayerSessionRegistry.Entry("0042", player2Id, "P2", player1Id);

        publisher.reconnected(entry);

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToUser(eq(player1Id.toString()), eq("/queue/room"), captor.capture());
        var envelope = (RoomEventPublisher.EventEnvelope<?>) captor.getValue();
        assertEquals("PLAYER_RECONNECTED", envelope.type());
        assertTrue(envelope.serverTime() > 0);
        assertEquals(new PlayerPresenceEventPublisher.PlayerPresencePayload("0042", "P2"), envelope.payload());
    }

    @Test
    void skipsRoomEventsWhenThereIsNoOpponent() {
        when(rooms.findByCodeAndStatusIn("0042", RoomCodeAllocator.ACTIVE))
                .thenReturn(of(new Room("0042", user(player1Id, "host"))));

        publisher.disconnected(new PlayerSessionRegistry.Entry("0042", player1Id, "P1", null));

        verifyNoInteractions(websocket);
    }

    private static User user(UUID id, String username) {
        User user = new User(username, "hashed-password", username + "@example.com");
        user.setId(id);
        return user;
    }
}
