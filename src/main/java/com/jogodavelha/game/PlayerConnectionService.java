package com.jogodavelha.game;

import com.jogodavelha.game.service.PlayerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class PlayerConnectionService {
    private final PlayerService players;
    private final PlayerSessionRegistry sessions;
    private final TaskScheduler scheduler;
    private final long disconnectGraceMillis;
    private final Map<PlayerRoom, PendingDisconnect> pendingDisconnects = new ConcurrentHashMap<>();
    private final Map<PlayerRoom, PlayerLock> locks = new ConcurrentHashMap<>();

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

    public ConnectResult connect(String sessionId, PlayerSessionRegistry.Entry entry) {
        PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
        return locked(key, () -> {
            PlayerSessionRegistry.Registration registration = sessions.register(sessionId, entry);
            try {
                boolean reconnected = players.markConnected(entry.roomCode(), entry.playerId());
                cancelPendingDisconnect(key);
                return new ConnectResult(registration.firstPlayerSession(),
                        registration.newSession() && registration.firstPlayerSession() && reconnected);
            } catch (RuntimeException failure) {
                if (registration.newSession()) {
                    sessions.removeWithStatus(sessionId);
                }
                throw failure;
            }
        });
    }

    public void disconnect(String sessionId, Consumer<PlayerSessionRegistry.Entry> onConfirmedDisconnect) {
        PlayerSessionRegistry.Removal removal = sessions.removeWithStatus(sessionId);
        if (removal == null || !removal.lastSession()) {
            return;
        }
        PlayerSessionRegistry.Entry entry = removal.entry();
        PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
        locked(key, () -> {
            if (!sessions.hasActiveSession(key.roomCode(), key.playerId())) {
                schedulePendingDisconnect(key, entry, onConfirmedDisconnect);
            }
            return null;
        });
    }

    @Scheduled(fixedDelayString = "${game.presence.refresh-interval:30000}")
    public void refreshActivePresence() {
        Set<PlayerRoom> refreshed = new HashSet<>();
        for (PlayerSessionRegistry.Entry entry : sessions.activeEntries()) {
            PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
            if (refreshed.add(key)) {
                locked(key, () -> {
                    if (sessions.hasActiveSession(key.roomCode(), key.playerId())) {
                        players.refreshConnected(key.roomCode(), key.playerId());
                    }
                    return null;
                });
            }
        }
    }

    private void schedulePendingDisconnect(PlayerRoom key, PlayerSessionRegistry.Entry entry,
                                           Consumer<PlayerSessionRegistry.Entry> onConfirmedDisconnect) {
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
        locked(key, () -> {
            if (pendingDisconnects.get(key) != pending) {
                return null;
            }
            pendingDisconnects.remove(key);
            if (!sessions.hasActiveSession(key.roomCode(), key.playerId())
                    && players.markDisconnected(key.roomCode(), key.playerId())) {
                pending.onConfirmedDisconnect().accept(pending.entry());
            }
            return null;
        });
    }

    private void cancelPendingDisconnect(PlayerRoom key) {
        PendingDisconnect pending = pendingDisconnects.remove(key);
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

    private <T> T locked(PlayerRoom key, Supplier<T> action) {
        PlayerLock lock = locks.compute(key, (k, current) -> {
            PlayerLock result = current != null ? current : new PlayerLock();
            result.users++;
            return result;
        });
        try {
            synchronized (lock.monitor) {
                return action.get();
            }
        } finally {
            locks.compute(key, (k, current) -> {
                if (current == lock) {
                    current.users--;
                    return current.users == 0 ? null : current;
                }
                return current;
            });
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

    private static final class PlayerLock {
        private final Object monitor = new Object();
        private int users;
    }

    private record PlayerRoom(String roomCode, UUID playerId) {}
}
