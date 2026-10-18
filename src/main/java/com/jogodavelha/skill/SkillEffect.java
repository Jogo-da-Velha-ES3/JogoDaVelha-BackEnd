package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;

public interface SkillEffect {
    /* Código estável da habilidade, igual a Skill.code no catálogo */
    String skillCode();

    boolean hasValidTarget(GameState state, SkillContext context);

    /* Só é chamado quando hasValidTarget é true */
    void apply(GameState state, SkillContext context);
}
