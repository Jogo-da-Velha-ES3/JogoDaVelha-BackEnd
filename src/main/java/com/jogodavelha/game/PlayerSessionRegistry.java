package com.jogodavelha.game;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class PlayerSessionRegistry {
    public record Entry(String roomCode, UUID playerId, String slot, UUID opponentId) {
        public Entry(String roomCode, UUID playerId) {
            this(roomCode, playerId, null, null);
        }
    }

    public record Registration(boolean newSession, boolean firstPlayerSession) {}
    public record Removal(Entry entry, boolean lastSession) {}

    private final Map<String, Entry> sessions = new HashMap<>();
    private final Map<PlayerRoom, Set<String>> playerSessions = new HashMap<>();

    public synchronized Registration register(String sessionId, Entry entry) {
        Objects.requireNonNull(sessionId, "A sessão STOMP é obrigatória.");
        Objects.requireNonNull(entry, "A presença da sessão é obrigatória.");
        Entry current = sessions.get(sessionId);
        if (entry.equals(current)) {
            return new Registration(false, false);
        }
        if (current != null) {
            throw new IllegalStateException("A sessão STOMP já está associada a outra presença.");
        }
        PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
        boolean firstSession = !playerSessions.containsKey(key);
        sessions.put(sessionId, entry);
        playerSessions.computeIfAbsent(key, ignored -> new HashSet<>()).add(sessionId);
        return new Registration(true, firstSession);
    }

    public synchronized Entry remove(String sessionId) {
        Removal removal = removeWithStatus(sessionId);
        return removal == null ? null : removal.entry();
    }

    public synchronized Removal removeWithStatus(String sessionId) {
        Entry entry = sessions.remove(sessionId);
        if (entry == null) {
            return null;
        }
        PlayerRoom key = new PlayerRoom(entry.roomCode(), entry.playerId());
        Set<String> active = playerSessions.get(key);
        boolean lastSession = active == null || active.isEmpty();
        if (active != null) {
            active.remove(sessionId);
            lastSession = active.isEmpty();
        }
        if (lastSession) {
            playerSessions.remove(key);
        }
        return new Removal(entry, lastSession);
    }

    public synchronized List<Entry> activeEntries() {
        return List.copyOf(sessions.values());
    }

    private record PlayerRoom(String roomCode, UUID playerId) {}
}
