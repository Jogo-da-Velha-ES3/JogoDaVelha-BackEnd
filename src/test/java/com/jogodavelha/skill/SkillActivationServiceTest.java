package com.jogodavelha.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.*;
import com.jogodavelha.game.service.TurnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillActivationServiceTest {
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private GameState state;
    private GameStateManager states;
    private TurnService turns;
    private SkillActivationService activations;
    private SkillEngine engine;
    private FakeEffect effect;

    @BeforeEach
    void setUp() {
        state = GameState.createInitial(gameId, p1, p2, 10_000, new java.security.SecureRandom());
        state.setRoundSkillDeck(p1, List.of("CONGELAR"));
        state.setRoundSkillDeck(p2, List.of("CONGELAR"));
        state.setCurrentTurnPlayerId(p1);
        state.setTurnDeadlineEpochMillis(20_000);
        states = mock(GameStateManager.class);
        turns = mock(TurnService.class);
        doAnswer(invocation -> {
            Function<GameState, GameState> mutator = invocation.getArgument(1);
            mutator.apply(state);
            return null;
        }).when(states).update(eq(gameId), any());
        effect = new FakeEffect();
        engine = new SkillEngine(List.of(effect), new GameValidator());
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);
        activations = new SkillActivationService(states, engine, turns, clock);
        when(turns.endPlayerAction(any(), any(), anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void appliedActivationRecordsUseEndsTurnAndPublishesAfterUpdate() {
        var activation = activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0));

        assertEquals(SkillResult.APPLIED, activation.result());
        assertTrue(state.hasUsedSkill(p1, "CONGELAR"));
        assertEquals(1, effect.applies);
        verify(turns).endPlayerAction(state, p1, 1_000);
        verify(turns).publishAndSchedule(state, Optional.empty());
    }

    @Test
    void noValidTargetStillConsumesSkillAndAction() {
        effect.valid = false;
        var activation = activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0));

        assertEquals(SkillResult.NO_VALID_TARGET, activation.result());
        assertTrue(state.hasUsedSkill(p1, "CONGELAR"));
        verify(turns).endPlayerAction(state, p1, 1_000);
    }

    @Test
    void reusedSkillIsRejectedWithoutApplyingOrEndingTurn() {
        state.recordSkillUse(p1, "CONGELAR");

        var error = assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));
        assertEquals(SkillValidationException.Code.SKILL_ALREADY_USED, error.getCode());

        assertEquals(0, effect.applies);
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        verify(turns, never()).publishAndSchedule(any(), any());
    }

    @Test
    void skillOutsideCurrentRoundDeckIsRejectedBeforeEffectOrTurnConsumption() {
        state.setRoundSkillDeck(p1, List.of("BOMBA"));

        var error = assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));

        assertEquals(SkillValidationException.Code.SKILL_NOT_IN_DECK, error.getCode());
        assertEquals(0, effect.applies);
        assertFalse(state.hasUsedSkill(p1, "CONGELAR"));
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        verify(turns, never()).publishAndSchedule(any(), any());
    }

    @Test
    void selectedDrawnSkillIsAcceptedWhenItIsInRoundDeck() {
        // The round deck is authoritative; this code can be the selected draw, regardless of ownership.
        state.setRoundSkillDeck(p1, List.of("CONGELAR"));

        var activation = activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0));

        assertEquals(SkillResult.APPLIED, activation.result());
        assertTrue(state.hasUsedSkill(p1, "CONGELAR"));
        verify(turns).endPlayerAction(state, p1, 1_000);
    }

    @Test
    void invalidRequestDoesNotRecordUseOrEndAction() {
        effect.throwsOnValidate = true;

        assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));

        assertFalse(state.hasUsedSkill(p1, "CONGELAR"));
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        verify(turns, never()).publishAndSchedule(any(), any());
    }

    @Test
    void nullContextOrCasterIsRejectedBeforeRedisUpdateAndPublication() {
        assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", null));
        assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(null, 0)));

        verifyNoInteractions(states);
        verify(turns, never()).publishAndSchedule(any(), any());
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        assertEquals(0, effect.applies);
    }

    @Test
    void silenceOutOfTurnExpiredAndWrongPhaseAreRejectedWithoutConsumption() {
        state.getSilencedTurns().put(p1, 1);
        assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));
        state.getSilencedTurns().clear();

        state.setCurrentTurnPlayerId(p2);
        assertThrows(MoveValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));
        state.setCurrentTurnPlayerId(p1);
        state.setTurnDeadlineEpochMillis(1_000);
        assertThrows(MoveValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));
        state.setTurnDeadlineEpochMillis(20_000);
        state.setPhase(GamePhase.ROUND_FINISHED);
        assertThrows(MoveValidationException.class,
                () -> activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0)));

        assertFalse(state.hasUsedSkill(p1, "CONGELAR"));
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
    }

    @Test
    void forceFieldResultConsumesUseAndTurnEvenWithoutApplyingSkill() {
        state.getForceFieldTurns().put(p2, 2);

        var activation = activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0));

        assertEquals(SkillResult.BLOCKED_BY_FORCE_FIELD, activation.result());
        assertTrue(state.hasUsedSkill(p1, "CONGELAR"));
        assertFalse(state.getForceFieldTurns().containsKey(p2));
        assertEquals(0, effect.applies);
        verify(turns).endPlayerAction(state, p1, 1_000);
    }

    @Test
    void sameCodeCanBeUsedByOpponentAndDifferentCodeBySamePlayer() throws Exception {
        activations.activate(gameId, "CONGELAR", new SkillContext(p1, 0));
        state.setCurrentTurnPlayerId(p2);
        activations.activate(gameId, "CONGELAR", new SkillContext(p2, 0));
        assertTrue(state.hasUsedSkill(p1, "CONGELAR"));
        assertTrue(state.hasUsedSkill(p2, "CONGELAR"));

        ObjectMapper mapper = new ObjectMapper();
        GameState restored = mapper.readValue(mapper.writeValueAsBytes(state), GameState.class);
        assertTrue(restored.hasUsedSkill(p1, "CONGELAR"));
        restored.startRound(2, 10_000, 2_000);
        assertFalse(restored.hasUsedSkill(p1, "CONGELAR"));
        assertFalse(restored.hasUsedSkill(p2, "CONGELAR"));
    }

    private static final class FakeEffect implements SkillEffect {
        private int applies;
        private boolean valid = true;
        private boolean throwsOnValidate;

        public String skillCode() { return "CONGELAR"; }
        public boolean isOffensive() { return true; }
        public boolean hasValidTarget(GameState state, SkillContext context) {
            if (throwsOnValidate) throw new SkillValidationException(
                    SkillValidationException.Code.SKILL_INVALID_TARGET, "Alvo inválido");
            return valid;
        }
        public void apply(GameState state, SkillContext context) { applies++; }
    }
}
