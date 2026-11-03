package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

@Component
public class FreezeEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "CONGELAR";
    }

    @Override
    public boolean isOffensive() {
        return true;
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        OffensiveEffectSupport.validateOpponentPiece(state, context);
        return true;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        state.getFrozenPositions().put(context.targetPosition(), 2);
    }
}
