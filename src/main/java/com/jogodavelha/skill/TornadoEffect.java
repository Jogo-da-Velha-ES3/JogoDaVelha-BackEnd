package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

@Component
public class TornadoEffect implements SkillEffect {
    private static final int BOARD_WIDTH = 4;

    @Override
    public String skillName() {
        return "TORNADO";
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        if (!OffensiveEffectSupport.hasUnreinforcedOpponentPiece(state, context)
                || !OffensiveEffectSupport.isEmpty(state, context.destinationPosition())) {
            return false;
        }
        int source = context.targetPosition();
        int destination = context.destinationPosition();
        return Math.abs(source / BOARD_WIDTH - destination / BOARD_WIDTH)
                + Math.abs(source % BOARD_WIDTH - destination % BOARD_WIDTH) == 1;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        OffensiveEffectSupport.movePiece(state, context.targetPosition(), context.destinationPosition());
    }
}
