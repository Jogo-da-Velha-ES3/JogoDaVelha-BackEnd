package com.jogodavelha.skill;

import java.util.UUID;

/* Dados da jogada de habilidade. Valores aleatórios entram já sorteados (o mutator deve ser puro) */
public record SkillContext(UUID casterId, Integer targetPosition) { }
