package com.jogodavelha.player;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.PlayerConnectionService;
import com.jogodavelha.game.PlayerDisconnectionListener;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.websocket.PlayerConnectionController;
import com.jogodavelha.websocket.PlayerPresenceEventPublisher;
import com.jogodavelha.websocket.RoomSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerConnectionControllerTest {
    private final PlayerService playerService = mock(PlayerService.class);
    private final PlayerSessionRegistry sessions = new PlayerSessionRegistry();
    private final PlayerConnectionService connections = new PlayerConnectionService(playerService, sessions);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomSnapshotService snapshots = mock(RoomSnapshotService.class);
    private final PlayerPresenceEventPublisher presenceEvents = mock(PlayerPresenceEventPublisher.class);
    private final PlayerDisconnectionListener listener =
            new PlayerDisconnectionListener(connections, presenceEvents);
    private final PlayerConnectionController controller =
            new PlayerConnectionController(connections, rooms, snapshots, presenceEvents);

    private User p1;
    private User p2;
    private Room room;

    @BeforeEach
    void setUp() {
        p1 = user("host");
        p2 = user("guest");
        room = new Room("0042", p1);
        room.join(p2);
        when(rooms.findActiveMembership(eq("0042"), eq(RoomCodeAllocator.ACTIVE), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID playerId = invocation.getArgument(2);
                    return p1.getId().equals(playerId) || p2.getId().equals(playerId)
                            ? Optional.of(room) : Optional.empty();
                });
    }

    @Test
    void connectUsesAuthenticatedPrincipalAndDerivesP1FromRoomMembership() {
        SimpMessageHeaderAccessor headers = headers("session-123");

        controller.connect("0042", () -> p1.getId().toString(), headers);

        verify(playerService).markConnected("0042", p1.getId());
        verify(snapshots).publish("0042");
        verify(presenceEvents, never()).reconnected(any());
        assertEquals(new PlayerSessionRegistry.Entry("0042", p1.getId(), "P1", p2.getId()),
                sessions.activeEntries().getFirst());
        verify(rooms).findActiveMembership("0042", RoomCodeAllocator.ACTIVE, p1.getId());
    }

    @Test
    void connectDerivesP2AndNotifiesOnlyTheOpponentOnReconnect() {
        when(playerService.markConnected("0042", p2.getId())).thenReturn(true);
        var entry = new PlayerSessionRegistry.Entry("0042", p2.getId(), "P2", p1.getId());

        controller.connect("0042", () -> p2.getId().toString(), headers("session-p2"));

        verify(presenceEvents).reconnected(entry);
        verify(playerService).markConnected("0042", p2.getId());
        verify(snapshots).publish("0042");
        assertEquals(entry, sessions.activeEntries().getFirst());
    }

    @Test
    void connectRejectsMissingInvalidAndNonMemberPrincipalsBeforeChangingPresence() {
        SimpMessageHeaderAccessor headers = headers("session-123");

        assertThrows(AccessDeniedException.class, () -> controller.connect("0042", null, headers));
        assertThrows(AccessDeniedException.class,
                () -> controller.connect("0042", () -> "not-a-uuid", headers));
        assertThrows(AccessDeniedException.class,
                () -> controller.connect("0042", () -> UUID.randomUUID().toString(), headers));
        assertThrows(IllegalArgumentException.class,
                () -> controller.connect("bad", () -> p1.getId().toString(), headers));

        verifyNoInteractions(playerService, snapshots, presenceEvents);
        assertTrue(sessions.activeEntries().isEmpty());
    }

    @Test
    void syncUsesOnlyAuthenticatedPrincipalIdentity() {
        controller.sync("0042", () -> p1.getId().toString());

        verify(snapshots).sync("0042", p1.getId());
    }

    @Test
    void syncRejectsMissingOrInvalidPrincipal() {
        assertThrows(AccessDeniedException.class, () -> controller.sync("0042", null));
        assertThrows(AccessDeniedException.class, () -> controller.sync("0042", () -> "not-a-uuid"));

        verifyNoInteractions(snapshots);
    }

    @Test
    void disconnectNotifiesOnlyAfterTheLastSessionAndDuplicateDisconnectIsIgnored() {
        var entry = new PlayerSessionRegistry.Entry("0042", p1.getId(), "P1", p2.getId());
        connections.connect("session-a", entry);
        connections.connect("session-b", entry);
        when(playerService.markDisconnected("0042", p1.getId())).thenReturn(true);
        SessionDisconnectEvent event = disconnectEvent("session-a");
        SessionDisconnectEvent finalEvent = disconnectEvent("session-b");

        listener.handleDisconnect(event);
        verify(presenceEvents, never()).disconnected(any());
        listener.handleDisconnect(finalEvent);
        listener.handleDisconnect(finalEvent);

        verify(playerService).markDisconnected("0042", p1.getId());
        verify(presenceEvents, times(1)).disconnected(entry);
    }

    @Test
    void disconnectForP2UsesP2SlotAndP1AsRecipient() {
        var entry = new PlayerSessionRegistry.Entry("0042", p2.getId(), "P2", p1.getId());
        connections.connect("session-p2", entry);
        when(playerService.markDisconnected("0042", p2.getId())).thenReturn(true);

        listener.handleDisconnect(disconnectEvent("session-p2"));

        verify(presenceEvents).disconnected(entry);
        verify(playerService).markDisconnected("0042", p2.getId());
    }

    private static SimpMessageHeaderAccessor headers(String sessionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId(sessionId);
        return headers;
    }

    private static SessionDisconnectEvent disconnectEvent(String sessionId) {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn(sessionId);
        return event;
    }

    private static User user(String username) {
        User user = new User(username, "hashed-password", username + "@example.com");
        user.setId(UUID.randomUUID());
        return user;
    }
}
