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

    public record Player(List<String> options, String selected, boolean skipped) {
        public Player(List<String> options, String selected) {
            this(options, selected, false);
        }

        public Player {
            options = List.copyOf(options);
        }
    }

    public RoundDrawState select(UUID playerId, String ability) {
        Player player = players.get(playerId);
        if (player == null) throw new IllegalArgumentException("Jogador não pertence ao sorteio.");
        if (player.selected() != null) throw new IllegalStateException("Habilidade do round já confirmada.");
        if (player.skipped()) throw new IllegalStateException("O sorteio deste jogador já foi encerrado.");
        if (!player.options().contains(ability)) {
            throw new IllegalArgumentException("Escolha uma das três habilidades sorteadas.");
        }
        Map<UUID, Player> updated = new LinkedHashMap<>(players);
        updated.put(playerId, new Player(player.options(), ability));
        return new RoundDrawState(roomCode, round, deadline, updated);
    }

    /** Encerra o sorteio do jogador sem escolher: o deck permanece como está. */
    public RoundDrawState skip(UUID playerId) {
        Player player = players.get(playerId);
        if (player == null) throw new IllegalArgumentException("Jogador não pertence ao sorteio.");
        if (player.selected() != null || player.skipped()) {
            throw new IllegalStateException("O sorteio deste jogador já foi resolvido.");
        }
        Map<UUID, Player> updated = new LinkedHashMap<>(players);
        updated.put(playerId, new Player(player.options(), null, true));
        return new RoundDrawState(roomCode, round, deadline, updated);
    }

    public boolean completed() {
        return players.values().stream().allMatch(player -> player.selected() != null || player.skipped());
    }
}
