package com.jogodavelha.game.draft;

import java.util.List;
import java.util.UUID;

// A BE-038 consome este evento para criar a partida. Não é uma mensagem STOMP.
public record DraftCompleted(UUID roomId, String roomCode, List<Selection> players, boolean automatic) {
    public DraftCompleted {
        players = List.copyOf(players);
    }

    public record Selection(UUID playerId, UUID characterId, List<String> initialSkills) {
        public Selection {
            initialSkills = List.copyOf(initialSkills);
        }
    }

    public static DraftCompleted from(DraftState state, boolean automatic) {
        if (!state.completed()) throw new IllegalArgumentException("O Draft ainda não terminou.");
        return new DraftCompleted(state.roomId(), state.roomCode(),
                List.of(selection(state.player1()), selection(state.player2())), automatic);
    }

    private static Selection selection(DraftState.Player player) {
        return new Selection(player.playerId(), player.characterId(), player.initialSkills());
    }
}
