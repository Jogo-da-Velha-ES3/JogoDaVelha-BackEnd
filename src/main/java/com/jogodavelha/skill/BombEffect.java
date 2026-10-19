package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

@Component
public class BombEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "BOMBA";
    }

    @Override
    public boolean isOffensive() {
        return true;
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        return OffensiveEffectSupport.hasUnreinforcedOpponentPiece(state, context);
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        OffensiveEffectSupport.removePiece(state, context.targetPosition());
    }
}
