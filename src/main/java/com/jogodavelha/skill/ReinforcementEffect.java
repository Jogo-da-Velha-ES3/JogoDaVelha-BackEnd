package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

@Component
public class ReinforcementEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "REFORCO";
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        DefensiveEffectSupport.validateOwnPiece(state, context);
        return true;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        state.getReinforcedPositions().put(context.targetPosition(), 2);
    }
}
