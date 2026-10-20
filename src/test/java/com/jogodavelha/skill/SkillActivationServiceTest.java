package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameValidator;
import com.jogodavelha.game.logic.SkillValidationException;
import com.jogodavelha.game.service.SkillActivationService;
import com.jogodavelha.game.service.TurnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillActivationServiceTest {
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final long now = 1_000;
    private GameStateManager manager;
    private TurnService turns;
    private ApplicationEventPublisher events;
    private GameState state;
    private SkillActivationService activations;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @BeforeEach
    void setUp() {
        manager = mock(GameStateManager.class);
        turns = mock(TurnService.class);
        events = mock(ApplicationEventPublisher.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        SkillEngine engine = new SkillEngine(List.of(new TestEffect("TEST"), new TestEffect("OTHER")), new GameValidator());
        TurnService realTurns = new TurnService(manager, engine, clock, scheduler, events);
        // Observe the actual TurnService action logic while retaining its post-commit publish boundary.
        doAnswer(invocation -> realTurns.endPlayerAction(invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2))).when(turns).endPlayerAction(any(), any(), anyLong());

        state = GameState.createInitial(gameId, p1, p2, 10_000,
                new java.security.SecureRandom());
        state.setBoard("X---------------");
        state.setCurrentTurnPlayerId(p1);
        state.setTurnDeadlineEpochMillis(now + 10_000);
        doAnswer(invocation -> {
            Function<GameState, GameState> mutator = invocation.getArgument(1);
            mutator.apply(state);
            return null;
        }).when(manager).update(eq(gameId), any());
        activations = new SkillActivationService(manager, engine, turns, clock);
    }

    @Test
    void appliedSkillIsPersistentlyConsumedAndEndsTurnViaTurnService() {
        assertEquals(SkillResult.APPLIED, activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        assertTrue(state.hasUsedSkill(p1, "TEST"));
        assertEquals(p2, state.getCurrentTurnPlayerId());
        verify(turns).endPlayerAction(state, p1, now);
        verify(turns).publishAndSchedule(state, Optional.empty());
    }

    @Test
    void noValidTargetStillConsumesSkillAndTurn() {
        state.setBoard("----------------");
        assertEquals(SkillResult.NO_VALID_TARGET,
                activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        assertTrue(state.hasUsedSkill(p1, "TEST"));
        assertEquals(p2, state.getCurrentTurnPlayerId());
        verify(turns).publishAndSchedule(state, Optional.empty());
    }

    @Test
    void forceFieldBlockConsumesSkillAndAction() {
        state.getForceFieldTurns().put(p2, 2);
        assertEquals(SkillResult.BLOCKED_BY_FORCE_FIELD,
                activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        assertTrue(state.hasUsedSkill(p1, "TEST"));
        assertFalse(state.getForceFieldTurns().containsKey(p2));
        assertEquals(p2, state.getCurrentTurnPlayerId());
        verify(turns).publishAndSchedule(state, Optional.empty());
    }

    @Test
    void invalidRequestDoesNotConsumeSkillOrTurn() {
        state.setBoard("----------------");
        assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "TEST", new SkillContext(p1, 99)));
        assertFalse(state.hasUsedSkill(p1, "TEST"));
        assertEquals(p1, state.getCurrentTurnPlayerId());
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        verify(turns, never()).publishAndSchedule(any(), any());
    }

    @Test
    void sameSkillIsRejectedButOpponentAndOtherCodeCanUseIt() {
        activations.activate(gameId, "TEST", new SkillContext(p1, null));
        state.setCurrentTurnPlayerId(p1);
        assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        assertEquals(p1, state.getCurrentTurnPlayerId());
        state.setBoard("X---------------");
        assertEquals(SkillResult.APPLIED, activations.activate(gameId, "OTHER",
                new SkillContext(p1, null)));

        state.setCurrentTurnPlayerId(p2);
        state.setBoard("X---------------");
        assertEquals(SkillResult.APPLIED, activations.activate(gameId, "TEST", new SkillContext(p2, null)));
        assertTrue(state.hasUsedSkill(p1, "TEST"));
        assertTrue(state.hasUsedSkill(p2, "TEST"));
    }

    @Test
    void rejectsOutsideTurnPhaseAndExpiredDeadline() {
        state.setCurrentTurnPlayerId(p2);
        assertThrows(RuntimeException.class,
                () -> activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        state.setCurrentTurnPlayerId(p1);
        state.setPhase(GamePhase.DRAFT);
        assertThrows(RuntimeException.class,
                () -> activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        state.setPhase(GamePhase.PLAYING);
        state.setTurnDeadlineEpochMillis(now);
        assertThrows(RuntimeException.class,
                () -> activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        assertFalse(state.hasUsedSkill(p1, "TEST"));
    }

    @Test
    void silenceRejectionDoesNotConsumeOrEndAction() {
        state.getSilencedTurns().put(p1, 2);
        SkillValidationException error = assertThrows(SkillValidationException.class,
                () -> activations.activate(gameId, "TEST", new SkillContext(p1, null)));
        assertEquals(SkillValidationException.Code.SKILL_SILENCED, error.getCode());
        assertFalse(state.hasUsedSkill(p1, "TEST"));
        assertEquals(p1, state.getCurrentTurnPlayerId());
        verify(turns, never()).endPlayerAction(any(), any(), anyLong());
        verify(turns, never()).publishAndSchedule(any(), any());
    }

    private static class TestEffect implements SkillEffect {
        private final String code;
        private TestEffect(String code) { this.code = code; }
        @Override public String skillCode() { return code; }
        @Override public boolean isOffensive() { return true; }
        @Override public boolean hasValidTarget(GameState game, SkillContext context) {
            if (context.targetPosition() != null && (context.targetPosition() < 0 || context.targetPosition() >= 16)) {
                throw new SkillValidationException(SkillValidationException.Code.SKILL_INVALID_TARGET, "invalid");
            }
            return game.getBoard().charAt(0) == 'X';
        }
        @Override public void apply(GameState game, SkillContext context) { game.setBoard("O" + game.getBoard().substring(1)); }
    }
}
