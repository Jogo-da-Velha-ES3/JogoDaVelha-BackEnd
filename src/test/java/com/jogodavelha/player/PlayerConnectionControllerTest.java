package com.jogodavelha.player;

import com.jogodavelha.game.PlayerDisconnectionListener;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.websocket.PlayerConnectionController;
import com.jogodavelha.websocket.RoomSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.UUID;
import java.security.Principal;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerConnectionControllerTest {

    private final PlayerService playerService = mock(PlayerService.class);
    private final PlayerSessionRegistry playerSessionRegistry = mock(PlayerSessionRegistry.class);
    private final RoomSnapshotService snapshots = mock(RoomSnapshotService.class);
    private final PlayerDisconnectionListener listener = new PlayerDisconnectionListener(playerService, playerSessionRegistry);
    private final PlayerConnectionController controller = new PlayerConnectionController(playerService, playerSessionRegistry);

    PlayerConnectionControllerTest() {
        controller.setSnapshots(snapshots);
    }

    @Test
    void connectUsesAuthenticatedPrincipalAndRegistersPresence() {
        UUID authenticatedId = UUID.randomUUID();
        UUID spoofedId = UUID.randomUUID();
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId("session-123");

        controller.connect("0042", () -> authenticatedId.toString(), headers);

        verify(playerSessionRegistry).register("session-123", "0042", authenticatedId);
        verify(playerService).markConnected("0042", authenticatedId);
        verify(snapshots).publish("0042");
        verify(playerService, never()).markConnected("0042", spoofedId);
    }

    @Test
    void connectRejectsMissingOrInvalidPrincipalBeforeChangingState() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId("session-123");

        assertThrows(AccessDeniedException.class, () -> controller.connect("0042", null, headers));
        Principal invalid = () -> "not-a-uuid";
        assertThrows(AccessDeniedException.class, () -> controller.connect("0042", invalid, headers));

        verifyNoInteractions(playerSessionRegistry, playerService);
    }

    @Test
    void syncUsesOnlyAuthenticatedPrincipalIdentity() {
        UUID authenticatedId = UUID.randomUUID();

        controller.sync("0042", () -> authenticatedId.toString());

        verify(snapshots).sync("0042", authenticatedId);
    }

    @Test
    void syncRejectsMissingOrInvalidPrincipal() {
        assertThrows(AccessDeniedException.class, () -> controller.sync("0042", null));
        assertThrows(AccessDeniedException.class, () -> controller.sync("0042", () -> "not-a-uuid"));

        verifyNoInteractions(snapshots);
    }

    @Test
    void marksPlayerDisconnectedWhenSessionIsKnown() {
        UUID playerId = UUID.randomUUID();
        PlayerSessionRegistry.Entry entry = new PlayerSessionRegistry.Entry("0042", playerId);
        when(playerSessionRegistry.remove("session-123")).thenReturn(entry);

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn("session-123");

        listener.handleDisconnect(event);

        verify(playerService).markDisconnected("0042", playerId);
    }

    @Test
    void doesNothingWhenSessionIsUnknown() {
        when(playerSessionRegistry.remove("session-999")).thenReturn(null);

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn("session-999");

        listener.handleDisconnect(event);

        verifyNoInteractions(playerService);
    }
}
