package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.SkillValidationException;
import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

import static com.jogodavelha.game.logic.SkillValidationException.Code.SKILL_INVALID_DESTINATION;

@Component
public class TornadoEffect implements SkillEffect {

    @Override
    public String skillCode() {
        return "TORNADO";
    }

    @Override
    public boolean isOffensive() {
        return true;
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        boolean unreinforced = OffensiveEffectSupport.hasUnreinforcedOpponentPiece(state, context);
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
        return unreinforced;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        OffensiveEffectSupport.movePiece(state, context.targetPosition(), context.destinationPosition());
    }
}
