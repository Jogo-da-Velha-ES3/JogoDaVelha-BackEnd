package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;

import java.util.UUID;

public record SkillResolvedEvent(UUID gameId, UUID playerId, String skillCode, Integer targetPosition,
                                 Integer destinationPosition, SkillResult result, GameState state) {

    public SkillResolvedEvent(UUID gameId, UUID playerId, String skillCode, Integer targetPosition,
                              Integer destinationPosition, SkillResult result) {
        this(gameId, playerId, skillCode, targetPosition, destinationPosition, result, null);
    }
}
