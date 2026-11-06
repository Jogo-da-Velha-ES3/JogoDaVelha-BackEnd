package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.SkillValidationException;
import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

import static com.jogodavelha.game.logic.SkillValidationException.Code.SKILL_INVALID_DESTINATION;

@Component
public class QuickSwapEffect implements SkillEffect {

    @Override
    public String skillCode() {
        return "TROCA_RAPIDA";
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        DefensiveEffectSupport.validateOwnPiece(state, context);
        int source = context.targetPosition();
        Integer destination = context.destinationPosition();
        if (destination == null && IntStream.range(0, GameState.BOARD_SIZE)
                .noneMatch(position -> BoardAdjacency.isAdjacent(source, position)
                        && OffensiveEffectSupport.isAvailableDestination(state, context, position))) {
            return false;
        }
        if (!OffensiveEffectSupport.isAvailableDestination(state, context, destination)
                || !BoardAdjacency.isAdjacent(source, destination)) {
            throw new SkillValidationException(SKILL_INVALID_DESTINATION,
                    "O destino deve ser uma casa vizinha na horizontal, vertical ou diagonal, vazia e sem bloqueio por Escudo.");
        }
        return true;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        Integer reinforcement = state.getReinforcedPositions().get(context.targetPosition());
        OffensiveEffectSupport.movePiece(state, context.targetPosition(), context.destinationPosition());
        if (reinforcement != null) {
            state.getReinforcedPositions().put(context.destinationPosition(), reinforcement);
        }
    }
}
