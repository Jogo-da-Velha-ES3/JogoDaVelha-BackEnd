package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

@Component
public class ForceFieldEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "CAMPO_DE_FORCA";
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        return true;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        state.getForceFieldTurns().put(context.casterId(), 2);
    }
}
