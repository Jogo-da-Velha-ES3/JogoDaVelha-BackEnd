package com.jogodavelha.game.service;

import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.MoveValidationException;
import com.jogodavelha.game.logic.SkillValidationException;
import com.jogodavelha.skill.SkillContext;
import com.jogodavelha.skill.SkillEngine;
import com.jogodavelha.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

import static com.jogodavelha.game.logic.MoveValidationException.Code.*;
import static com.jogodavelha.game.logic.SkillValidationException.Code.SKILL_ALREADY_USED;

/** Domain integration for a valid skill activation and the action it consumes. */
@Service
@RequiredArgsConstructor
public class SkillActivationService {
    private static final String BLACK_HOLE = "BURACO_NEGRO";
    private final GameStateManager stateManager;
    private final SkillEngine skillEngine;
    private final TurnService turnService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Values needed by effects (including random draws) must be prepared before this call. */
    public SkillResult activate(UUID gameId, String skillCode, SkillContext context) {
        long nowMillis = clock.millis();
        SkillContext preparedContext = prepareRandomValue(skillCode, context);
        GameState[] committedSnapshot = new GameState[1];
        Optional<Character>[] winner = winnerHolder();
        SkillResult[] result = new SkillResult[1];

        stateManager.update(gameId, state -> {
            // GameStateManager may retry this mutator: discard all values from a failed attempt.
            committedSnapshot[0] = null;
            winner[0] = Optional.empty();
            result[0] = null;

            validateTurn(state, preparedContext == null ? null : preparedContext.casterId(), nowMillis);
            UUID actorId = preparedContext.casterId();
            if (state.hasUsedSkill(actorId, skillCode)) {
                throw new SkillValidationException(SKILL_ALREADY_USED,
                        "Esta habilidade já foi usada por este jogador neste round.");
            }

            SkillResult applied = skillEngine.useSkill(state, skillCode, preparedContext);
            state.recordSkillUse(actorId, skillCode);
            winner[0] = turnService.endPlayerAction(state, actorId, nowMillis);
            result[0] = applied;
            committedSnapshot[0] = state;
            return state;
        });

        // update returns only after Redis CAS succeeds; never publish for a failed/retried attempt.
        turnService.publishAndSchedule(committedSnapshot[0], winner[0]);
        return result[0];
    }

    private SkillContext prepareRandomValue(String skillCode, SkillContext context) {
        if (!BLACK_HOLE.equals(skillCode) || context == null || context.randomValue() != null) {
            return context;
        }
        return new SkillContext(context.casterId(), context.targetPosition(), context.destinationPosition(),
                random.nextDouble());
    }

    private static void validateTurn(GameState state, UUID actorId, long nowMillis) {
        if (actorId == null) {
            throw new SkillValidationException(SkillValidationException.Code.SKILL_INVALID_TARGET,
                    "Informe o jogador que usa a habilidade.");
        }
        if (!actorId.equals(state.getPlayer1Id()) && !actorId.equals(state.getPlayer2Id())) {
            throw new SkillValidationException(SkillValidationException.Code.SKILL_INVALID_TARGET,
                    "O jogador não pertence a esta partida.");
        }
        if (state.getPhase() == null) {
            throw new MoveValidationException(INVALID_PHASE, "Informe a fase da partida.");
        }
        switch (state.getPhase()) {
            case DRAFT -> throw new MoveValidationException(DRAFT_IN_PROGRESS, "O Draft ainda não terminou.");
            case ROUND_FINISHED -> throw new MoveValidationException(ROUND_FINISHED, "O round já terminou.");
            case FINISHED -> throw new MoveValidationException(GAME_FINISHED, "A partida já terminou.");
            case PLAYING -> { }
        }
        if (!actorId.equals(state.getCurrentTurnPlayerId())) {
            throw new MoveValidationException(NOT_YOUR_TURN, "Não é o turno deste jogador.");
        }
        if (nowMillis >= state.getTurnDeadlineEpochMillis()) {
            throw new MoveValidationException(TURN_EXPIRED, "O prazo deste turno já terminou.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Character>[] winnerHolder() {
        return new Optional[]{Optional.empty()};
    }
}
