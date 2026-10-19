package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

@Component
public class SilenceEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "SILENCIO";
    }

    @Override
    public boolean isOffensive() {
        return true;
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        return true;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        state.getSilencedTurns().put(state.getOpponentId(context.casterId()), 2);
    }
}
