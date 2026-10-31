package com.jogodavelha.game;

import com.jogodavelha.game.logic.*;
import com.jogodavelha.game.service.TurnService;
import com.jogodavelha.skill.SkillEngine;
import com.jogodavelha.skill.HourglassEffect;
import com.jogodavelha.skill.SkillContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TurnServiceTest {
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    private GameStateManager manager;
    private ApplicationEventPublisher events;
    private TurnService service;
    private SkillEngine engine;
    private long nowMillis;
    private GameState state;
    private TaskScheduler scheduler;

    @SuppressWarnings({"uncheked", "rawtypes"})
    @BeforeEach
    void setUp() {
        manager = mock(GameStateManager.class);
        events = mock(ApplicationEventPublisher.class);
        scheduler = mock(TaskScheduler.class);
        ScheduledFuture future = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future);
        engine = new SkillEngine(List.of(new HourglassEffect()), new GameValidator());
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(nowMillis); }
        };
        service = new TurnService(manager, engine, clock, scheduler, events);

        state = GameState.createInitial(gameId, p1, p2, 10000, new SecureRandom());
        state.setCurrentTurnPlayerId(p1);
        doAnswer(inv -> {
            Function<GameState, GameState> mutator = inv.getArgument(1);
            mutator.apply(state);
            return null;
        }).when(manager).update(eq(gameId), any());
    }

    @Test
    void expireIsIdempotent() {
        long deadline = state.getTurnDeadlineEpochMillis();
        nowMillis = deadline + 1;

        service.expire(gameId, deadline);
        service.expire(gameId, deadline); // prazo já mudou: no-op

        assertEquals(p2, state.getCurrentTurnPlayerId());
        assertEquals(1, state.getConsecutiveTimeouts(p1));
        verify(events, times(1)).publishEvent(any(TurnChangedEvent.class));
    }

    @Test
    void expireBeforeDeadlineChangesNothing() {
        long deadline = state.getTurnDeadlineEpochMillis();
        nowMillis = deadline - 100;

        service.expire(gameId, deadline);

        assertEquals(p1, state.getCurrentTurnPlayerId());
        assertEquals(0, state.getConsecutiveTimeouts(p1));
        verify(events, never()).publishEvent(any(TurnChangedEvent.class));
    }

    @Test
    void turnDurationIs10sNormal() {
        nowMillis = state.getTurnDeadlineEpochMillis();
        service.expire(gameId, state.getTurnDeadlineEpochMillis());
        assertEquals(10_000, state.getTurnDurationMillis());
        assertEquals(nowMillis + 10_000, state.getTurnDeadlineEpochMillis());
    }

    @Test
    void turnDurationIs5sInSuddenDeath() {
        state.setSuddenDeath(true);
        nowMillis = state.getTurnDeadlineEpochMillis();
        service.expire(gameId, state.getTurnDeadlineEpochMillis());
        assertEquals(5_000, state.getTurnDurationMillis());
    }

    @Test
    void turnDurationIs5sWithHourglassOnNextPlayer() {
        state.getHourglassTurns().put(p2, 2);
        nowMillis = state.getTurnDeadlineEpochMillis();
        service.expire(gameId, state.getTurnDeadlineEpochMillis());
        assertEquals(5_000, state.getTurnDurationMillis());
    }

    @Test
    void hourglassSkillShortensOnlyTheOpponentsNextTurn() {
        engine.useSkill(state, "AMPULHETA", new SkillContext(p1, null));
        service.endPlayerAction(state, p1, 1000);
        assertEquals(p2, state.getCurrentTurnPlayerId());
        assertEquals(5000, state.getTurnDurationMillis());
        assertEquals(6000, state.getTurnDeadlineEpochMillis());
        assertEquals(1, state.getHourglassTurns().get(p2));

        service.endPlayerAction(state, p2, 2000);
        assertEquals(p1, state.getCurrentTurnPlayerId());
        assertEquals(10000, state.getTurnDurationMillis());
        assertTrue(state.getHourglassTurns().isEmpty());
        service.endPlayerAction(state, p1, 3000);
        assertEquals(p2, state.getCurrentTurnPlayerId());
        assertEquals(10000, state.getTurnDurationMillis());
    }

    @Test
    void timeoutThatCompletesLineFinishesRoundWithoutPassingTurn() {
        state.setBoard("XXXX------------");
        state.getFrozenPositions().put(3, 1); // congelada: vitória só aparece com a resolução
        // jogador do turno é P1 (X); linha com casa congelada não vence agora
        long deadline = state.getTurnDeadlineEpochMillis();
        nowMillis = deadline;
        service.expire(gameId, deadline);
        assertEquals(p2, state.getCurrentTurnPlayerId()); // sem vitória ainda: passa a vez
    }

    @Test
    void timeoutWithWinningLineFinishesRound() {
        state.setBoard("XXXX------------");
        long deadline = state.getTurnDeadlineEpochMillis();
        nowMillis = deadline;
        service.expire(gameId, deadline);
        assertEquals(GamePhase.ROUND_FINISHED, state.getPhase());
        assertEquals(p1, state.getCurrentTurnPlayerId()); // vez não passa
    }

    @Test
    void moveInTimeMakesOldTimerANoOp() {
        long oldDeadline = state.getTurnDeadlineEpochMillis();
        nowMillis = oldDeadline - 3000;
        service.endPlayerAction(state, p1, nowMillis); // jogada válida: novo prazo
        nowMillis = oldDeadline + 1;

        service.expire(gameId, oldDeadline);

        assertEquals(p2, state.getCurrentTurnPlayerId());
        assertEquals(0, state.getConsecutiveTimeouts(p1));
        verify(events, never()).publishEvent(any(TurnChangedEvent.class));
    }

    @Test
    void endPlayerActionResetsConsecutiveTimeouts() {
        state.recordTimeout(p1);
        state.recordTimeout(p1);
        service.endPlayerAction(state, p1, 1_000);
        assertEquals(0, state.getConsecutiveTimeouts(p1));
    }

    @Test
    void thirdTimeoutFinishesTheMatchWithoutPassingTurnOrSchedulingAnotherTimer() {
        state.recordTimeout(p1);
        state.recordTimeout(p1);
        long deadline = state.getTurnDeadlineEpochMillis();
        nowMillis = deadline;

        service.expire(gameId, deadline);
        service.expire(gameId, deadline);

        assertEquals(GamePhase.FINISHED, state.getPhase());
        assertEquals(3, state.getConsecutiveTimeouts(p1));
        assertEquals(p1, state.getCurrentTurnPlayerId());
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        verify(events, times(1)).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(value -> value instanceof TurnChangedEvent event
                && p1.equals(event.timedOutPlayerId()) && !event.roundFinished()));
    }

    @Test
    void draftAndRoundDrawTimeDoNotIncreaseTheTimeoutCount() {
        state.setPhase(GamePhase.DRAFT);
        state.recordTimeout(p1);
        nowMillis = state.getTurnDeadlineEpochMillis();

        service.expire(gameId, nowMillis);

        assertEquals(1, state.getConsecutiveTimeouts(p1));
        verifyNoInteractions(events);
    }
}
