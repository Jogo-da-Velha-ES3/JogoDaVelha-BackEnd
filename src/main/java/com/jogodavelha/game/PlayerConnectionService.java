package com.jogodavelha.game;

import com.jogodavelha.game.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerConnectionService {
    private final PlayerService players;
    private final PlayerSessionRegistry sessions;

    public synchronized ConnectResult connect(String sessionId, PlayerSessionRegistry.Entry entry) {
        PlayerSessionRegistry.Registration registration = sessions.register(sessionId, entry);
        try {
            boolean reconnected = players.markConnected(entry.roomCode(), entry.playerId());
            return new ConnectResult(registration.firstPlayerSession(),
                    registration.newSession() && registration.firstPlayerSession() && reconnected);
        } catch (RuntimeException failure) {
            if (registration.newSession()) {
                sessions.removeWithStatus(sessionId);
            }
            throw failure;
        }
    }

    public synchronized Optional<PlayerSessionRegistry.Entry> disconnect(String sessionId) {
        PlayerSessionRegistry.Removal removal = sessions.removeWithStatus(sessionId);
        if (removal == null || !removal.lastSession()) {
            return Optional.empty();
        }
        PlayerSessionRegistry.Entry entry = removal.entry();
        return players.markDisconnected(entry.roomCode(), entry.playerId())
                ? Optional.of(entry) : Optional.empty();
    }

    @Scheduled(fixedDelayString = "${game.presence.refresh-interval:30000}")
    public synchronized void refreshActivePresence() {
        Set<PlayerRoom> refreshed = new HashSet<>();
        for (PlayerSessionRegistry.Entry entry : sessions.activeEntries()) {
            PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
            if (refreshed.add(key)) {
                players.refreshConnected(entry.roomCode(), entry.playerId());
            }
        }
    }

    public record ConnectResult(boolean firstPlayerSession, boolean reconnected) {}

    private record PlayerRoom(String roomCode, UUID playerId) {}
}
