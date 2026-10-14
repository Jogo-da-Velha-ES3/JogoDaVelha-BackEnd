package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;

public interface SkillEffect {
    /* Nome da habilidade, igual a Skill.name no banco */
    String skillName();

    boolean hasValidTarget(GameState state, SkillContext context);

    /* Só é chamado quando hasValidTarget é true */
    void apply(GameState state, SkillContext context);
}
