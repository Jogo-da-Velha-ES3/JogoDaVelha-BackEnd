package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameValidator;
import org.springframework.stereotype.Component;

import java.util.*;

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
        if (!effect.hasValidTarget(state, context)) {
            return SkillResult.NO_VALID_TARGET;
        }
        effect.apply(state, context);

        return SkillResult.APPLIED;
    }

    /*
    * Fim da ação (jogada, habilidade ou timeout): resolveWinner com os efeitos ainda ativos
    * e só depois diminui os contadores. Um único ponto garante a ordem
    */
    public Optional<Character> endAction(GameState state, UUID actorId) {
        Optional<Character> winner = validator.resolveWinner(
                state.getBoard(), state.frozenSet(), state.getPlayerSymbol(actorId));
        state.tickEffects();
        return winner;
    }

}
