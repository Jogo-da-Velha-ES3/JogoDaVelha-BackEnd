package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.SkillValidationException;
import org.springframework.stereotype.Component;

import static com.jogodavelha.game.logic.SkillValidationException.Code.SKILL_INVALID_TARGET;

@Component
public class ShieldEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "ESCUDO";
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        if (!OffensiveEffectSupport.isAvailableDestination(state, context, context.targetPosition())) {
            throw new SkillValidationException(SKILL_INVALID_TARGET,
                    "O alvo do Escudo deve ser uma casa vazia e sem bloqueio do oponente.");
        }
        return true;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        state.getShieldedPositions().put(context.targetPosition(), new GameState.ShieldEffect(context.casterId(), 2));
    }
}
