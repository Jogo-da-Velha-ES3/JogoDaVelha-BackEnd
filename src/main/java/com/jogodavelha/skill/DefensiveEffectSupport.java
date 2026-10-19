package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.SkillValidationException;

import static com.jogodavelha.game.logic.SkillValidationException.Code.SKILL_INVALID_TARGET;

final class DefensiveEffectSupport {
    private DefensiveEffectSupport() { }

    static void validateOwnPiece(GameState state, SkillContext context) {
        if (!OffensiveEffectSupport.isPosition(context.targetPosition())
                || state.getBoard().charAt(context.targetPosition()) != state.getPlayerSymbol(context.casterId())) {
            throw new SkillValidationException(SKILL_INVALID_TARGET,
                    "O alvo deve ser uma peça própria entre as posições 0 e 15.");
        }
    }
}
