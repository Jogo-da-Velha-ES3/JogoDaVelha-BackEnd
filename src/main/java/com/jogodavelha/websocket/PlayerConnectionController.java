package com.jogodavelha.websocket;

import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PlayerConnectionController {

    private final PlayerService playerService;
    private final PlayerSessionRegistry playerSessionRegistry;
    private RoomSnapshotService snapshots;

    @Autowired(required = false)
    public void setSnapshots(RoomSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @MessageMapping("/rooms/{code}/connect")
    public void connect(
            @DestinationVariable String code,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Authenticated STOMP principal is required");
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("Authenticated STOMP principal is invalid", exception);
        }
        String sessionId = headerAccessor.getSessionId();
        if (sessionId == null) {
            throw new AccessDeniedException("STOMP session is required");
        }

        playerSessionRegistry.register(sessionId, code, playerId);
        playerService.markConnected(code, playerId);
        if (snapshots != null) snapshots.publish(code);
    }

    @MessageMapping("/rooms/{code}/sync")
    public void sync(@DestinationVariable String code, Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authenticated STOMP principal is required");
        }
        UUID requesterId;
        try {
            requesterId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AccessDeniedException("Authenticated STOMP principal is invalid", exception);
        }
        if (snapshots == null) {
            throw new IllegalStateException("Room snapshot service is unavailable.");
        }
        snapshots.sync(code, requesterId);
    }
}
