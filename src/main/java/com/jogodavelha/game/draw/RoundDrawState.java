package com.jogodavelha.game.draw;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoundDrawState(String roomCode, int round, long deadline, Map<UUID, Player> players) {
    public static final long DURATION_MILLIS = 8_000;

    public RoundDrawState {
        players = Map.copyOf(players);
    }

    public record Player(List<String> options, String selected) {
        public Player {
            options = List.copyOf(options);
        }
    }

    public RoundDrawState select(UUID playerId, String ability) {
        Player player = players.get(playerId);
        if (player == null) throw new IllegalArgumentException("Jogador não pertence ao sorteio.");
        if (player.selected() != null) throw new IllegalStateException("Habilidade do round já confirmada.");
        if (!player.options().contains(ability)) {
            throw new IllegalArgumentException("Escolha uma das três habilidades sorteadas.");
        }
        Map<UUID, Player> updated = new LinkedHashMap<>(players);
        updated.put(playerId, new Player(player.options(), ability));
        return new RoundDrawState(roomCode, round, deadline, updated);
    }

    public boolean completed() {
        return players.values().stream().allMatch(player -> player.selected() != null);
    }
}
