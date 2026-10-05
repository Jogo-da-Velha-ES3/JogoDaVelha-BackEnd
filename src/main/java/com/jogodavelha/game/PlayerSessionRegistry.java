package com.jogodavelha.game;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlayerSessionRegistry {
    public record Entry(String roomCode, UUID playerId) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String roomCode, UUID playerId) {
        sessions.put(sessionId, new Entry(roomCode, playerId));
    }

    public Entry remove(String sessionId) {
        return sessions.remove(sessionId);
    }
}
