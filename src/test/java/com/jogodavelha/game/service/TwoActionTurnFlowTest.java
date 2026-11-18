package com.jogodavelha.game.service;

import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameValidator;
import com.jogodavelha.game.logic.MoveValidator;
import com.jogodavelha.skill.SkillActivationService;
import com.jogodavelha.skill.SkillContext;
import com.jogodavelha.skill.SkillEffect;
import com.jogodavelha.skill.SkillEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwoActionTurnFlowTest {
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final long now = 1_000L;
    private final long deadline = 11_000L;
    private java.util.concurrent.atomic.AtomicLong timerNow;
    private GameState state;
    private GameStateManager states;
    private ApplicationEventPublisher events;
    private TaskScheduler scheduler;
    private MoveActionService moves;
    private SkillActivationService skills;
    private TurnService turns;

    @BeforeEach
    void setUp() {
        state = GameState.createInitial(gameId, p1, p2, 10_000, new java.security.SecureRandom());
        state.setCurrentTurnPlayerId(p1);
        state.setTurnDeadlineEpochMillis(deadline);
        state.setRoundSkillDeck(p1, List.of("TEST"));
        states = mock(GameStateManager.class);
        doAnswer(invocation -> {
            Function<GameState, GameState> mutator = invocation.getArgument(1);
            synchronized (state) {
                return mutator.apply(state);
            }
        }).when(states).update(eq(gameId), any());
        events = mock(ApplicationEventPublisher.class);
        scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        timerNow = new java.util.concurrent.atomic.AtomicLong(now);
        Clock timerClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(timerNow.get()); }
        };
        SkillEngine engine = new SkillEngine(List.of(new TestEffect()), new GameValidator());
        turns = new TurnService(states, engine, timerClock, scheduler, events);
        moves = new MoveActionService(states, new MoveValidator(clock), turns, clock, events);
        skills = new SkillActivationService(states, engine, turns, clock, events);
    }

    @Test
    void skillThenMoveUsesOneDeadlineAndPublishesOneTurnStart() {
        skills.activate(gameId, "TEST", new SkillContext(p1, null));
        assertEquals(p1, state.getCurrentTurnPlayerId());
        assertEquals(deadline, state.getTurnDeadlineEpochMillis());
        assertTrue(state.isAbilityUsedThisTurn());
        verify(events, never()).publishEvent(any(TurnChangedEvent.class));
        verifyNoInteractions(scheduler);

        moves.play(gameId, p1, 0);
        assertTurnCompleted();
    }

    @Test
    void moveThenSkillUsesOneDeadlineAndPublishesOneTurnStart() {
        moves.play(gameId, p1, 0);
        assertEquals(p1, state.getCurrentTurnPlayerId());
        assertEquals(deadline, state.getTurnDeadlineEpochMillis());
        assertTrue(state.isMoveUsedThisTurn());
        verify(events, never()).publishEvent(any(TurnChangedEvent.class));
        verifyNoInteractions(scheduler);

        skills.activate(gameId, "TEST", new SkillContext(p1, null));
        assertTurnCompleted();
    }

    @Test
    void concurrentMoveAndSkillAreBothAcceptedOnceAndCompleteOnlyOneTurn() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> move = pool.submit(() -> {
                ready.countDown();
                start.await();
                moves.play(gameId, p1, 0);
                accepted.incrementAndGet();
                return null;
            });
            Future<?> ability = pool.submit(() -> {
                ready.countDown();
                start.await();
                skills.activate(gameId, "TEST", new SkillContext(p1, null));
                accepted.incrementAndGet();
                return null;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            move.get(5, TimeUnit.SECONDS);
            ability.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, accepted.get());
        assertTurnCompleted();
        verify(events, times(1)).publishEvent(any(TurnChangedEvent.class));
        verify(events, times(2)).publishEvent(any(com.jogodavelha.game.GameplayBoardUpdatedEvent.class));
        verify(events, times(1)).publishEvent(any(com.jogodavelha.skill.SkillResolvedEvent.class));
    }

    @Test
    void concurrentDuplicateMovesAcceptOnlyOneAndDoNotStartASecondTurn() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> attemptMove(ready, start, accepted));
            Future<?> second = pool.submit(() -> attemptMove(ready, start, accepted));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, accepted.get());
        assertTrue(state.isMoveUsedThisTurn());
        assertEquals(p1, state.getCurrentTurnPlayerId());
        verify(events, times(1)).publishEvent(any(com.jogodavelha.game.GameplayBoardUpdatedEvent.class));
        verify(events, never()).publishEvent(any(TurnChangedEvent.class));
        verifyNoInteractions(scheduler);
    }

    @Test
    void concurrentDuplicateSkillsAcceptOnlyOne() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> attemptSkill(ready, start, accepted));
            Future<?> second = pool.submit(() -> attemptSkill(ready, start, accepted));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, accepted.get());
        assertTrue(state.isAbilityUsedThisTurn());
        assertEquals(p1, state.getCurrentTurnPlayerId());
        verify(events, times(1)).publishEvent(any(com.jogodavelha.skill.SkillResolvedEvent.class));
        verify(events, times(1)).publishEvent(any(com.jogodavelha.game.GameplayBoardUpdatedEvent.class));
        verify(events, never()).publishEvent(any(TurnChangedEvent.class));
        verifyNoInteractions(scheduler);
    }

    @Test
    void actionRacingWithDeadlineProducesOneTransitionAndNoFalseWo() throws Exception {
        timerNow.set(deadline);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> action = pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    moves.play(gameId, p1, 0); // action clock is before the stored deadline
                } catch (com.jogodavelha.game.logic.MoveValidationException racedByTimeout) {
                    // Timeout may win the atomic state update; then this action is rejected.
                }
                return null;
            });
            Future<?> timeout = pool.submit(() -> {
                ready.countDown();
                start.await();
                turns.expire(gameId, deadline);
                return null;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            action.get(5, TimeUnit.SECONDS);
            timeout.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(p2, state.getCurrentTurnPlayerId());
        assertTrue(state.getConsecutiveTimeouts(p1) <= 1);
        verify(events, times(1)).publishEvent(any(TurnChangedEvent.class));
        verify(events, times(1)).publishEvent(any(com.jogodavelha.game.TurnTimedOutEvent.class));
    }

    private Void attemptMove(CountDownLatch ready, CountDownLatch start, AtomicInteger accepted) {
        ready.countDown();
        try {
            start.await();
            moves.play(gameId, p1, 0);
            accepted.incrementAndGet();
        } catch (com.jogodavelha.game.logic.MoveValidationException duplicate) {
            // A second successful move is not allowed in the same turn.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return null;
    }

    private Void attemptSkill(CountDownLatch ready, CountDownLatch start, AtomicInteger accepted) {
        ready.countDown();
        try {
            start.await();
            skills.activate(gameId, "TEST", new SkillContext(p1, null));
            accepted.incrementAndGet();
        } catch (com.jogodavelha.game.logic.SkillValidationException duplicate) {
            // A second skill cannot consume the action already committed in Redis.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return null;
    }

    private void assertTurnCompleted() {
        assertEquals(p2, state.getCurrentTurnPlayerId());
        assertEquals(now + GameState.DEFAULT_TURN_DURATION_MILLIS, state.getTurnDeadlineEpochMillis());
        assertFalse(state.isAbilityUsedThisTurn());
        assertFalse(state.isMoveUsedThisTurn());
        verify(events, times(1)).publishEvent(any(TurnChangedEvent.class));
        verify(scheduler, times(1)).schedule(any(Runnable.class), eq(Instant.ofEpochMilli(state.getTurnDeadlineEpochMillis())));
    }

    private static final class TestEffect implements SkillEffect {
        @Override public String skillCode() { return "TEST"; }
        @Override public boolean isOffensive() { return false; }
        @Override public boolean hasValidTarget(GameState state, SkillContext context) { return true; }
        @Override public void apply(GameState state, SkillContext context) { }
    }
}
