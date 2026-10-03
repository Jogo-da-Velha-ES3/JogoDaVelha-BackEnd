package com.jogodavelha.player;

import com.jogodavelha.game.PlayerDisconnectionListener;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.UUID;

import static org.mockito.Mockito.*;

class PlayerConnectionControllerTest {

    private final PlayerService playerService = mock(PlayerService.class);
    private final PlayerSessionRegistry playerSessionRegistry = mock(PlayerSessionRegistry.class);
    private final PlayerDisconnectionListener listener = new PlayerDisconnectionListener(playerService, playerSessionRegistry);

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