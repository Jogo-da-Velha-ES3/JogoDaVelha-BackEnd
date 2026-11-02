package com.jogodavelha.game;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlayerSessionRegistry {
    public record Entry(String roomCode, UUID playerId) {}
    public record Removal(Entry entry, boolean lastSessionForPlayer) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public synchronized void register(String sessionId, String roomCode, UUID playerId) {
        sessions.put(sessionId, new Entry(roomCode, playerId));
    }

    public synchronized Entry remove(String sessionId) {
        return sessions.remove(sessionId);
    }

    public synchronized Removal removeAndCheckLast(String sessionId) {
        Entry entry = sessions.remove(sessionId);
        if (entry == null) return new Removal(null, false);
        boolean last = sessions.values().stream().noneMatch(other -> other.roomCode().equals(entry.roomCode())
                && other.playerId().equals(entry.playerId()));
        return new Removal(entry, last);
    }
}
