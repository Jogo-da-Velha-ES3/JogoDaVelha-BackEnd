package com.jogodavelha.game.draft;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

public interface DraftEligibilityProvider {
    EligibleItems forPlayer(UUID playerId);

    record EligibleItems(List<UUID> characters, List<String> skills) {
        public EligibleItems {
            characters = List.copyOf(new TreeSet<>(Objects.requireNonNull(characters)));
            skills = List.copyOf(new TreeSet<>(Objects.requireNonNull(skills)));
            if (characters.isEmpty() || skills.size() < 2 || skills.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("O Draft exige personagem elegível e pelo menos duas habilidades.");
            }
        }
    }
}
