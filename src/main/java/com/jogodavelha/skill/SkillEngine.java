package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameValidator;
import com.jogodavelha.game.logic.SkillValidationException;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jogodavelha.game.logic.SkillValidationException.Code.*;

@Component
public class SkillEngine {
    private final Map<String, SkillEffect> effects = new HashMap<>();
    private final GameValidator validator;

    public SkillEngine(List<SkillEffect> skillEffects, GameValidator validator) {
        for (SkillEffect effect : skillEffects) {
            String skillCode = effect.skillCode();
            if (effects.put(skillCode, effect) != null) {
                throw new IllegalStateException("Habilidade duplicada: " + skillCode);
            }
        }
        this.validator = validator;
    }

    /* Pedido inválido lança exceção; falha de regra retorna NO_VALID_TARGET, que conta como uso. */
    public SkillResult useSkill(GameState state, String skillCode, SkillContext context) {
        SkillEffect effect = effects.get(skillCode);
        if (effect == null) {
            throw new IllegalArgumentException("Habilidade desconhecida: " + skillCode);
        }
        if (context == null || context.casterId() == null) {
            throw new SkillValidationException(SKILL_INVALID_TARGET, "Informe o jogador que usa a habilidade.");
        }
        UUID opponent = state.getOpponentId(context.casterId());
        if (state.getSilencedTurns().getOrDefault(context.casterId(), 0) > 0) {
            throw new SkillValidationException(SKILL_SILENCED, "Silêncio impede o uso de habilidades neste turno.");
        }
        boolean hasValidTarget = effect.hasValidTarget(state, context);
        if (effect.isOffensive() && state.getForceFieldTurns().getOrDefault(opponent, 0) > 0) {
            state.getForceFieldTurns().remove(opponent);
            return SkillResult.BLOCKED_BY_FORCE_FIELD;
        }
        if (!hasValidTarget) {
            return SkillResult.NO_VALID_TARGET;
        }
        effect.apply(state, context);

        return SkillResult.APPLIED;
    }

    /** Verifica resultado após uma ação, mantendo efeitos ativos até o fim do turno completo. */
    public Optional<Character> resolveWinner(GameState state, UUID actorId) {
        return validator.resolveWinner(state.getBoard(), state.frozenSet(), state.getPlayerSymbol(actorId));
    }

}
