package com.jogodavelha.websocket;

import com.jogodavelha.game.PlayerConnectionService;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PlayerConnectionController {
    private final PlayerConnectionService connections;
    private final RoomRepository rooms;
    private final RoomSnapshotService snapshots;
    private final PlayerPresenceEventPublisher presenceEvents;

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
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }

        Room room = rooms.findActiveMembership(code, RoomCodeAllocator.ACTIVE, playerId)
                .orElseThrow(() -> new AccessDeniedException("Jogador não pertence à sala."));
        PlayerSessionRegistry.Entry entry = presenceEntry(room, code, playerId);
        PlayerConnectionService.ConnectResult result = connections.connect(sessionId, entry);
        if (result.reconnected()) {
            presenceEvents.reconnected(entry);
        }
        snapshots.publish(code);
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
        snapshots.sync(code, requesterId);
    }

    private PlayerSessionRegistry.Entry presenceEntry(Room room, String code, UUID playerId) {
        if (room.getPlayer1().getId().equals(playerId)) {
            UUID opponentId = room.getPlayer2() == null ? null : room.getPlayer2().getId();
            return new PlayerSessionRegistry.Entry(code, playerId, "P1", opponentId);
        }
        if (room.getPlayer2() != null && room.getPlayer2().getId().equals(playerId)) {
            return new PlayerSessionRegistry.Entry(code, playerId, "P2", room.getPlayer1().getId());
        }
        throw new AccessDeniedException("Jogador não pertence à sala.");
    }
}
