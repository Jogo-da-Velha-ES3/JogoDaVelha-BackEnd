package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.MoveValidationException;
import com.jogodavelha.game.logic.SkillValidationException;
import com.jogodavelha.game.service.TurnService;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.jogodavelha.game.logic.MoveValidationException.Code.*;

/** Orquestra uma ativação como ação atômica persistida do jogador. */
@Service
public class SkillActivationService {
    public record Activation(GameState state, SkillResult result, Optional<Character> winner) {}

    private final GameStateManager states;
    private final SkillEngine skills;
    private final TurnService turns;
    private final Clock clock;

    public SkillActivationService(GameStateManager states, SkillEngine skills, TurnService turns, Clock clock) {
        this.states = states;
        this.skills = skills;
        this.turns = turns;
        this.clock = clock;
    }

    /** Atualiza o Redis e só então publica/agendada a mudança de turno. */
    public Activation activate(UUID gameId, String skillCode, SkillContext requestedContext) {
        if (requestedContext == null || requestedContext.casterId() == null) {
            throw new SkillValidationException(SkillValidationException.Code.SKILL_INVALID_TARGET,
                    "Informe o jogador que usa a habilidade.");
        }
        long now = clock.millis();
        SkillContext context = requestedContext;
        if ("BURACO_NEGRO".equals(skillCode) && context.randomValue() == null) {
            context = new SkillContext(context.casterId(), context.targetPosition(), context.destinationPosition(),
                    ThreadLocalRandom.current().nextDouble());
        }
        final SkillContext preparedContext = context;
        SkillResult[] result = new SkillResult[1];
        Optional<Character>[] winner = new Optional[]{Optional.empty()};
        GameState[] committed = new GameState[1];

        states.update(gameId, state -> {
            // update pode reexecutar o mutator depois de conflito: resultados precisam pertencer a esta tentativa.
            result[0] = null;
            winner[0] = Optional.empty();
            committed[0] = null;
            var actor = preparedContext.casterId();
            try {
                state.getPlayerSymbol(actor); // valida que o jogador pertence à partida
            } catch (IllegalArgumentException exception) {
                throw new AccessDeniedException("Jogador não pertence à partida.", exception);
            }
            if (state.getPhase() == null || state.getPhase() != GamePhase.PLAYING) {
                throw new MoveValidationException(state.getPhase() == GamePhase.DRAFT ? DRAFT_IN_PROGRESS
                        : state.getPhase() == GamePhase.ROUND_FINISHED ? ROUND_FINISHED
                        : state.getPhase() == GamePhase.FINISHED ? GAME_FINISHED : INVALID_PHASE,
                        "A partida não está aceitando ações neste momento.");
            }
            if (!actor.equals(state.getCurrentTurnPlayerId())) {
                throw new MoveValidationException(NOT_YOUR_TURN, "Não é o turno deste jogador.");
            }
            if (now >= state.getTurnDeadlineEpochMillis()) {
                throw new MoveValidationException(TURN_EXPIRED, "O prazo deste turno já terminou.");
            }
            if (state.hasUsedSkill(actor, skillCode)) {
                throw new SkillValidationException(SkillValidationException.Code.SKILL_ALREADY_USED,
                        "Esta habilidade já foi utilizada neste round.");
            }
            if (!state.hasSkillInRoundDeck(actor, skillCode)) {
                throw new SkillValidationException(SkillValidationException.Code.SKILL_NOT_IN_DECK,
                        "Esta habilidade não está no deck deste round.");
            }

            SkillResult applied = skills.useSkill(state, skillCode, preparedContext);
            // APPLIED, NO_VALID_TARGET e BLOCKED_BY_FORCE_FIELD são ativações válidas que consomem ação.
            state.recordSkillUse(actor, skillCode);
            result[0] = applied;
            winner[0] = turns.endPlayerAction(state, actor, now);
            committed[0] = state;
            return state;
        });

        if (committed[0] == null || result[0] == null) {
            throw new IllegalStateException("A ativação não produziu um estado persistido.");
        }
        turns.publishAndSchedule(committed[0], winner[0]);
        return new Activation(committed[0], result[0], winner[0]);
    }
}
