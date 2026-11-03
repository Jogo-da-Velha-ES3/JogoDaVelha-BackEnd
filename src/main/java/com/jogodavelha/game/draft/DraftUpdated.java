package com.jogodavelha.game.draft;

import java.util.List;
import java.util.UUID;

// Evento interno: não divulga inventários ou habilidades escolhidas ao oponente.
public record DraftUpdated(UUID roomId, String roomCode, long deadlineEpochMillis, long version,
                           List<PlayerStatus> players) {
    public DraftUpdated {
        players = List.copyOf(players);
    }

    public record PlayerStatus(UUID playerId, UUID characterId, DraftState.Stage stage, boolean ready) { }

    public static DraftUpdated from(DraftState state) {
        return new DraftUpdated(state.roomId(), state.roomCode(), state.deadlineEpochMillis(), state.version(),
                List.of(status(state.player1()), status(state.player2())));
    }

    private static PlayerStatus status(DraftState.Player player) {
        return new PlayerStatus(player.playerId(), player.characterId(), player.stage(), player.ready());
    }
}
