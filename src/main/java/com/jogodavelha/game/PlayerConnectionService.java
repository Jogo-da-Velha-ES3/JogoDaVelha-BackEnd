package com.jogodavelha.game;

import com.jogodavelha.game.service.PlayerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class PlayerConnectionService {
    private final PlayerService players;
    private final PlayerSessionRegistry sessions;
    private final TaskScheduler scheduler;
    private final long disconnectGraceMillis;
    private final Map<PlayerRoom, PendingDisconnect> pendingDisconnects = new HashMap<>();

    public PlayerConnectionService(PlayerService players, PlayerSessionRegistry sessions,
                                   @Qualifier("turnTaskScheduler") TaskScheduler scheduler,
                                   @Value("${game.presence.disconnect-grace-millis:1500}") long disconnectGraceMillis) {
        if (disconnectGraceMillis < 0) {
            throw new IllegalArgumentException("A janela de tolerância de desconexão não pode ser negativa.");
        }
        this.players = players;
        this.sessions = sessions;
        this.scheduler = scheduler;
        this.disconnectGraceMillis = disconnectGraceMillis;
    }

    public synchronized ConnectResult connect(String sessionId, PlayerSessionRegistry.Entry entry) {
        PlayerSessionRegistry.Registration registration = sessions.register(sessionId, entry);
        try {
            boolean reconnected = players.markConnected(entry.roomCode(), entry.playerId());
            cancelPendingDisconnect(entry.roomCode(), entry.playerId());
            return new ConnectResult(registration.firstPlayerSession(),
                    registration.newSession() && registration.firstPlayerSession() && reconnected);
        } catch (RuntimeException failure) {
            if (registration.newSession()) {
                sessions.removeWithStatus(sessionId);
            }
            throw failure;
        }
    }

    public synchronized void disconnect(String sessionId,
                                        Consumer<PlayerSessionRegistry.Entry> onConfirmedDisconnect) {
        PlayerSessionRegistry.Removal removal = sessions.removeWithStatus(sessionId);
        if (removal == null || !removal.lastSession()) {
            return;
        }
        schedulePendingDisconnect(removal.entry(), onConfirmedDisconnect);
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

    private void schedulePendingDisconnect(PlayerSessionRegistry.Entry entry,
                                           Consumer<PlayerSessionRegistry.Entry> onConfirmedDisconnect) {
        PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
        PendingDisconnect pending = new PendingDisconnect(entry, onConfirmedDisconnect);
        PendingDisconnect previous = pendingDisconnects.put(key, pending);
        if (previous != null) {
            cancel(previous);
        }
        pending.future().set(scheduler.schedule(
                () -> confirmDisconnect(key, pending),
                Instant.now().plusMillis(disconnectGraceMillis)));
    }

    private void confirmDisconnect(PlayerRoom key, PendingDisconnect pending) {
        synchronized (this) {
            if (pendingDisconnects.get(key) != pending) {
                return;
            }
            pendingDisconnects.remove(key);
            if (!sessions.hasActiveSession(key.roomCode(), key.playerId())
                    && players.markDisconnected(key.roomCode(), key.playerId())) {
                pending.onConfirmedDisconnect().accept(pending.entry());
            }
        }
    }

    private void cancelPendingDisconnect(String roomCode, UUID playerId) {
        PendingDisconnect pending = pendingDisconnects.remove(new PlayerRoom(roomCode, playerId));
        if (pending != null) {
            cancel(pending);
        }
    }

    private void cancel(PendingDisconnect pending) {
        ScheduledFuture<?> future = pending.future().get();
        if (future != null) {
            future.cancel(false);
        }
    }

    public record ConnectResult(boolean firstPlayerSession, boolean reconnected) {}

    private record PendingDisconnect(PlayerSessionRegistry.Entry entry,
                                     Consumer<PlayerSessionRegistry.Entry> onConfirmedDisconnect,
                                     AtomicReference<ScheduledFuture<?>> future) {
        PendingDisconnect(PlayerSessionRegistry.Entry entry,
                          Consumer<PlayerSessionRegistry.Entry> onConfirmedDisconnect) {
            this(entry, onConfirmedDisconnect, new AtomicReference<>());
        }
    }

    private record PlayerRoom(String roomCode, UUID playerId) {}
}
