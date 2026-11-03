package com.jogodavelha.skill;

import java.util.UUID;
import java.util.random.RandomGenerator;

/* Dados da jogada de habilidade. Valores aleatórios entram já sorteados (o mutator deve ser puro) */
public record SkillContext(UUID casterId, Integer targetPosition, Integer destinationPosition, Double randomValue) {
    public SkillContext(UUID casterId, Integer targetPosition) {
        this(casterId, targetPosition, null, null);
    }

    public SkillContext(UUID casterId, Integer targetPosition, Integer destinationPosition) {
        this(casterId, targetPosition, destinationPosition, null);
    }

    public SkillContext {
        if (randomValue != null && !(randomValue >= 0 && randomValue < 1)) {
            throw new IllegalArgumentException("O valor sorteado deve estar entre 0 (inclusivo) e 1 (exclusivo).");
        }
    }

    public static SkillContext randomized(UUID casterId, Integer targetPosition, RandomGenerator random) {
        return new SkillContext(casterId, targetPosition, null, random.nextDouble());
    }
}
