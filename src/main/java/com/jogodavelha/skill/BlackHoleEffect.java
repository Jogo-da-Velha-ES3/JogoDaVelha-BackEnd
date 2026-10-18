package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

@Component
public class BlackHoleEffect implements SkillEffect {
    @Override
    public String skillCode() {
        return "BURACO_NEGRO";
    }

    @Override
    public boolean hasValidTarget(GameState state, SkillContext context) {
        return OffensiveEffectSupport.hasUnreinforcedOpponentPiece(state, context)
                && state.getBoard().indexOf('-') >= 0;
    }

    @Override
    public void apply(GameState state, SkillContext context) {
        if (context.randomValue() == null) {
            throw new IllegalArgumentException("Prepare o sorteio do Buraco Negro antes do update.");
        }
        int[] emptyPositions = IntStream.range(0, GameState.BOARD_SIZE)
                .filter(position -> state.getBoard().charAt(position) == '-')
                .toArray();
        int destination = emptyPositions[(int) (context.randomValue() * emptyPositions.length)];
        OffensiveEffectSupport.movePiece(state, context.targetPosition(), destination);
    }
}
