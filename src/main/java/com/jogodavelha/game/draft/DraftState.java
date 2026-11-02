package com.jogodavelha.game.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DraftState(UUID roomId, String roomCode, long deadlineEpochMillis, long version,
                         Player player1, Player player2) {
    public static final long DURATION_MILLIS = 20_000;

    public enum Stage { CHARACTER, SKILLS, READY }

    public DraftState {
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(player1);
        Objects.requireNonNull(player2);
        if (roomCode == null || roomCode.isBlank() || deadlineEpochMillis <= 0 || version < 0
                || player1.playerId().equals(player2.playerId())) {
            throw new IllegalArgumentException("Informe sala, prazo e dois jogadores diferentes para o Draft.");
        }
        if (!canCompleteCharacters(player1, player2)) {
            throw new IllegalArgumentException("Não há combinação de personagens distintos para os dois jogadores.");
        }
    }

    public boolean completed() {
        return player1.ready() && player2.ready();
    }

    public Player player(UUID playerId) {
        if (player1.playerId().equals(playerId)) return player1;
        if (player2.playerId().equals(playerId)) return player2;
        throw new IllegalArgumentException("Jogador não pertence ao Draft.");
    }

    public DraftState withPlayer(Player updated) {
        player(updated.playerId());
        return new DraftState(roomId, roomCode, deadlineEpochMillis, version,
                player1.playerId().equals(updated.playerId()) ? updated : player1,
                player2.playerId().equals(updated.playerId()) ? updated : player2);
    }

    public DraftState withVersion(long updatedVersion) {
        return new DraftState(roomId, roomCode, deadlineEpochMillis, updatedVersion, player1, player2);
    }

    // Os valores são sorteados antes do update; retries só recalculam as opções ainda disponíveis.
    public DraftState autoComplete(List<Double> draws) {
        if (draws.size() != 6 || draws.stream().anyMatch(value -> value == null || !(value >= 0 && value < 1))) {
            throw new IllegalArgumentException("Informe seis valores sorteados entre 0 e 1.");
        }
        Player first = player1;
        Player second = player2;
        if (first.characterId() == null) {
            List<UUID> secondOptions = characterOptions(second);
            List<UUID> options = first.eligibleCharacters().stream()
                    .filter(character -> secondOptions.stream().anyMatch(other -> !character.equals(other)))
                    .toList();
            first = first.withCharacter(pick(options, draws.get(0)));
        }
        if (second.characterId() == null) {
            UUID reserved = first.characterId();
            second = second.withCharacter(pick(second.eligibleCharacters().stream()
                    .filter(character -> !character.equals(reserved)).toList(), draws.get(3)));
        }
        first = completeSkills(first, draws.get(1), draws.get(2));
        second = completeSkills(second, draws.get(4), draws.get(5));
        return new DraftState(roomId, roomCode, deadlineEpochMillis, version, first, second);
    }

    private static Player completeSkills(Player player, double firstDraw, double secondDraw) {
        if (player.ready()) return player;
        List<String> options = new ArrayList<>(player.eligibleSkills());
        String first = pick(options, firstDraw);
        options.remove(first);
        return player.withSkills(List.of(first, pick(options, secondDraw)));
    }

    private static <T> T pick(List<T> options, double draw) {
        if (options.isEmpty()) throw new IllegalStateException("Não há item elegível disponível para auto-escolha.");
        return options.get((int) (draw * options.size()));
    }

    private static List<UUID> characterOptions(Player player) {
        return player.characterId() == null ? player.eligibleCharacters() : List.of(player.characterId());
    }

    private static boolean canCompleteCharacters(Player first, Player second) {
        return characterOptions(first).stream().anyMatch(character ->
                characterOptions(second).stream().anyMatch(other -> !character.equals(other)));
    }

    public Player opponentOf(UUID playerId) {
        player(playerId); // valida pertencimento
        return player1.playerId().equals(playerId) ? player2 : player1;
    }

    public record Player(UUID playerId, List<UUID> eligibleCharacters, List<String> eligibleSkills,
                         UUID characterId, List<String> initialSkills) {
        public Player {
            Objects.requireNonNull(playerId);
            var eligible = new DraftEligibilityProvider.EligibleItems(eligibleCharacters, eligibleSkills);
            eligibleCharacters = eligible.characters();
            eligibleSkills = eligible.skills();
            initialSkills = List.copyOf(initialSkills);
            if (characterId != null && !eligibleCharacters.contains(characterId)) {
                throw new IllegalArgumentException("Personagem não pertence ao inventário do jogador.");
            }
            if (!initialSkills.isEmpty() && (characterId == null || initialSkills.size() != 2
                    || initialSkills.get(0).equals(initialSkills.get(1)) || !eligibleSkills.containsAll(initialSkills))) {
                throw new IllegalArgumentException("Escolha personagem antes de confirmar duas habilidades elegíveis distintas.");
            }
        }

        public static Player initial(UUID playerId, DraftEligibilityProvider.EligibleItems items) {
            return new Player(playerId, items.characters(), items.skills(), null, List.of());
        }

        public Stage stage() {
            return ready() ? Stage.READY : characterId == null ? Stage.CHARACTER : Stage.SKILLS;
        }

        public boolean ready() {
            return characterId != null && initialSkills.size() == 2;
        }

        public Player withCharacter(UUID character) {
            if (ready()) throw new IllegalStateException("Escolhas do jogador já estão confirmadas.");
            return new Player(playerId, eligibleCharacters, eligibleSkills, Objects.requireNonNull(character), initialSkills);
        }

        public Player withSkills(List<String> skills) {
            if (ready()) throw new IllegalStateException("Escolhas do jogador já estão confirmadas.");
            if (characterId == null || skills.size() != 2) {
                throw new IllegalArgumentException("Confirme o personagem e duas habilidades.");
            }
            return new Player(playerId, eligibleCharacters, eligibleSkills, characterId, skills);
        }
    }
}
