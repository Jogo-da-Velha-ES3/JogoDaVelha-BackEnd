package com.jogodavelha.skill;

import java.util.UUID;

public record SkillResolvedEvent(UUID gameId, UUID playerId, String skillCode, Integer targetPosition,
                                 Integer destinationPosition, SkillResult result) { }
