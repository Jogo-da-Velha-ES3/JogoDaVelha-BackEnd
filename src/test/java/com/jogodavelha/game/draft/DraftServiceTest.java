package com.jogodavelha.game.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class DraftServiceTest {
    private final UUID room = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final String key = "draft:state:" + room;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> stored = new AtomicReference<>();
    private final AtomicLong now = new AtomicLong(1000);
    private final ConcurrentLinkedQueue<Object> published = new ConcurrentLinkedQueue<>();
    private final DraftEligibilityProvider eligibility = mock(DraftEligibilityProvider.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> timer = mock(ScheduledFuture.class);
    private final RandomGenerator random = mock(RandomGenerator.class);
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private DraftStateManager states;
    private DraftService service;

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        var items = new DraftEligibilityProvider.EligibleItems(List.of(a, b), List.of("BOMBA", "ESCUDO", "AMPULHETA"));
        when(eligibility.forPlayer(p1)).thenReturn(items);
        when(eligibility.forPlayer(p2)).thenReturn(items);
        when(random.nextDouble()).thenReturn(0.25);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn((ScheduledFuture) timer);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        doAnswer(invocation -> { published.add(invocation.getArgument(0)); return null; })
                .when(events).publishEvent(any(Object.class));
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(key)).thenAnswer(ignored -> stored.get());
        when(values.setIfAbsent(eq(key), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> stored.compareAndSet(null, invocation.getArgument(1)));
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenAnswer(this::compareAndSet);
        states = new DraftStateManager(redis, mapper, 1800000, 5);
        service = new DraftService(states, eligibility, clock, scheduler, events, random);
    }

    @Test
    void failedProgressListenerDoesNotSuppressCompletion() {
        ApplicationEventPublisher failingEvents = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("Falha ao notificar clientes"))
                .when(failingEvents).publishEvent(isA(DraftUpdated.class));
        Clock expired = Clock.fixed(Instant.ofEpochMilli(21000), java.time.ZoneOffset.UTC);
        DraftService failingService = new DraftService(states, eligibility, expired, scheduler,
                failingEvents, random);
        service.start(room, "0042", p1, p2);

        assertThrows(IllegalStateException.class, () -> failingService.expire(room, 21000));
        assertTrue(snapshot().completed());
        verify(failingEvents).publishEvent(isA(DraftCompleted.class));
    }

    @Test
    void startsWithOneTwentySecondDeadlineAndPublishesOnlyProgress() throws Exception {
        DraftState state = service.start(room, "0042", p1, p2);

        assertEquals(21000, state.deadlineEpochMillis());
        assertEquals(DraftState.Stage.CHARACTER, state.player1().stage());
        assertEquals(state, snapshot());
        verify(scheduler).schedule(any(Runnable.class), eq(Instant.ofEpochMilli(21000)));
        assertEquals(1, published.size());
        DraftUpdated event = (DraftUpdated) published.peek();
        assertFalse(event.players().getFirst().ready());
        String json = mapper.writeValueAsString(event);
        assertFalse(json.contains("eligible"));
        assertFalse(json.contains("initialSkills"));
        assertFalse(json.contains("BOMBA"));
        verifyNoInteractions(random);
    }

    @Test
    void earlyCompletionLocksChoicesCancelsTimerAndCompletesOnlyOnce() {
        service.start(room, "0042", p1, p2);
        service.updatePlayer(room, p1, player -> player.withCharacter(a));
        assertEquals(DraftState.Stage.SKILLS, snapshot().player1().stage());
        service.updatePlayer(room, p1, player -> player.withSkills(List.of("BOMBA", "ESCUDO")));
        assertTrue(snapshot().player1().ready());
        assertFalse(snapshot().player2().ready());
        assertEquals(0, completedCount());
        service.updatePlayer(room, p2, player -> player.withCharacter(b));
        service.updatePlayer(room, p2, player -> player.withSkills(List.of("AMPULHETA", "BOMBA")));

        assertTrue(snapshot().completed());
        assertEquals(4, snapshot().version());
        DraftCompleted event = completedEvent();
        assertFalse(event.automatic());
        assertEquals(room, event.roomId());
        assertEquals(List.of("BOMBA", "ESCUDO"), event.players().getFirst().initialSkills());
        verify(timer).cancel(false);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        assertThrows(IllegalStateException.class,
                () -> service.updatePlayer(room, p1, player -> player.withCharacter(b)));
        now.set(21000);
        service.expire(room, 21000);
        assertEquals(1, completedCount());
        verifyNoInteractions(random);
    }

    @Test
    void timeoutPreservesConfirmedChoicesAndFillsOnlyPendingChoicesFromTheSnapshot() {
        service.start(room, "0042", p1, p2);
        service.updatePlayer(room, p1, player -> player.withCharacter(a));
        service.updatePlayer(room, p1, player -> player.withSkills(List.of("BOMBA", "ESCUDO")));
        service.updatePlayer(room, p2, player -> player.withCharacter(b));
        DraftState.Player ready = snapshot().player1();
        now.set(21000);

        service.expire(room, 21000);
        service.expire(room, 21000);

        assertEquals(ready, snapshot().player1());
        assertEquals(b, snapshot().player2().characterId());
        assertEquals(List.of("AMPULHETA", "BOMBA"), snapshot().player2().initialSkills());
        assertTrue(completedEvent().automatic());
        assertEquals(1, completedCount());
        verify(random, times(6)).nextDouble();
        verify(eligibility, times(1)).forPlayer(p1);
        verify(eligibility, times(1)).forPlayer(p2);
    }

    @Test
    void timeoutWithNoSelectionsChoosesDistinctEligibleCharactersAndSkills() {
        service.start(room, "0042", p1, p2);
        now.set(21000);
        service.expire(room, 21000);

        DraftState state = snapshot();
        assertTrue(state.completed());
        assertNotEquals(state.player1().characterId(), state.player2().characterId());
        for (var player : List.of(state.player1(), state.player2())) {
            assertTrue(player.eligibleCharacters().contains(player.characterId()));
            assertEquals(2, player.initialSkills().stream().distinct().count());
            assertTrue(player.eligibleSkills().containsAll(player.initialSkills()));
        }
    }

    @Test
    void prematureTimerRearmsAndStaleTimerDoesNothing() {
        service.start(room, "0042", p1, p2);
        service.expire(room, 22000);
        service.expire(room, 21000);

        assertFalse(snapshot().completed());
        assertEquals(0, snapshot().version());
        assertEquals(1, published.size());
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq(Instant.ofEpochMilli(21000)));
        verifyNoInteractions(random);
    }

    @Test
    void choicesAtTheDeadlineAreRejectedAndAutoChoiceCanStillFinish() {
        service.start(room, "0042", p1, p2);
        now.set(21000);
        assertThrows(IllegalStateException.class,
                () -> service.updatePlayer(room, p1, player -> player.withCharacter(a)));
        assertEquals(0, snapshot().version());
        assertEquals(1, published.size());
        service.expire(room, 21000);
        assertTrue(snapshot().completed());
    }

    @Test
    void retriedChoiceRechecksTheClockAfterAConcurrentRedisVersionConflict() throws Exception {
        service.start(room, "0042", p1, p2);
        AtomicInteger attempts = new AtomicInteger();
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        stored.set(mapper.writeValueAsString(snapshot().withVersion(1)));
                        now.set(21000);
                        return 0L;
                    }
                    return compareAndSet(invocation);
                });

        assertThrows(IllegalStateException.class,
                () -> service.updatePlayer(room, p1, player -> player.withCharacter(a)));
        assertNull(snapshot().player1().characterId());
        assertEquals(1, snapshot().version());
    }

    @Test
    void rejectsWrongOrderIneligibleChoicesAndNonParticipantWithoutChangingState() {
        service.start(room, "0042", p1, p2);
        DraftState before = snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlayer(room, p1, player -> player.withSkills(List.of("BOMBA", "ESCUDO"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlayer(room, p1, player -> player.withCharacter(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlayer(room, UUID.randomUUID(), player -> player));
        assertEquals(before, snapshot());
        assertEquals(1, published.size());

        service.updatePlayer(room, p1, player -> player.withCharacter(a));
        DraftState selected = snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlayer(room, p1, player -> player.withSkills(List.of("BOMBA", "BOMBA"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlayer(room, p1, player -> player.withSkills(List.of("BOMBA", "INEXISTENTE"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePlayer(room, p2, player -> player.withCharacter(a)));
        assertEquals(selected, snapshot());
    }

    @Test
    void duplicateStartDoesNotResetDeadlineOrPublishAgain() {
        DraftState before = service.start(room, "0042", p1, p2);
        now.set(5000);
        assertThrows(IllegalStateException.class, () -> service.start(room, "0042", p1, p2));
        assertEquals(before, snapshot());
        assertEquals(1, published.size());
        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void twoConcurrentTimersPublishOnlyOneCompletion() throws Exception {
        service.start(room, "0042", p1, p2);
        now.set(21000);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger attempts = new AtomicInteger();
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() <= 2) barrier.await(5, TimeUnit.SECONDS);
                    return compareAndSet(invocation);
                });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.expire(room, 21000));
            var second = executor.submit(() -> service.expire(room, 21000));
            first.get(8, TimeUnit.SECONDS);
            second.get(8, TimeUnit.SECONDS);
        }

        assertTrue(snapshot().completed());
        assertNotEquals(snapshot().player1().characterId(), snapshot().player2().characterId());
        assertEquals(1, snapshot().version());
        assertEquals(1, completedCount());
        assertEquals(2, published.stream().filter(DraftUpdated.class::isInstance).count());
    }

    @Test
    void timeoutRetryKeepsTheLatestSelectionAndDoesNotDrawAgain() {
        service.start(room, "0042", p1, p2);
        now.set(21000);
        AtomicInteger attempts = new AtomicInteger();
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        DraftState concurrent = snapshot().withPlayer(snapshot().player1().withCharacter(b)).withVersion(1);
                        stored.set(mapper.writeValueAsString(concurrent));
                        return 0L;
                    }
                    return compareAndSet(invocation);
                });

        service.expire(room, 21000);

        assertEquals(b, snapshot().player1().characterId());
        assertEquals(a, snapshot().player2().characterId());
        assertEquals(2, snapshot().version());
        assertEquals(1, completedCount());
        verify(random, times(6)).nextDouble();
    }

    @Test
    void timerWinningAgainstTheLastConfirmationCannotCompleteTheDraftTwice() throws Exception {
        service.start(room, "0042", p1, p2);
        service.updatePlayer(room, p1, player -> player.withCharacter(a));
        service.updatePlayer(room, p1, player -> player.withSkills(List.of("BOMBA", "ESCUDO")));
        service.updatePlayer(room, p2, player -> player.withCharacter(b));
        now.set(20999);
        CountDownLatch manualAtCas = new CountDownLatch(1);
        CountDownLatch releaseManual = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        manualAtCas.countDown();
                        if (!releaseManual.await(5, TimeUnit.SECONDS)) throw new AssertionError("Confirmação não liberada.");
                    }
                    return compareAndSet(invocation);
                });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var manual = executor.submit(() -> service.updatePlayer(room, p2,
                    player -> player.withSkills(List.of("BOMBA", "ESCUDO"))));
            try {
                assertTrue(manualAtCas.await(5, TimeUnit.SECONDS));
                now.set(21000);
                service.expire(room, 21000);
            } finally {
                releaseManual.countDown();
            }
            var rejected = assertThrows(ExecutionException.class, () -> manual.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, rejected.getCause());
        }

        assertTrue(snapshot().completed());
        assertEquals(4, snapshot().version());
        assertEquals(1, completedCount());
        assertTrue(completedEvent().automatic());
    }

    @Test
    void autoChoiceLeavesTheOnlyEligibleCharacterForThePlayerWhoNeedsIt() {
        when(eligibility.forPlayer(p2)).thenReturn(new DraftEligibilityProvider.EligibleItems(
                List.of(a), List.of("BOMBA", "ESCUDO")));
        service.start(room, "0042", p1, p2);
        now.set(21000);

        service.expire(room, 21000);

        assertEquals(b, snapshot().player1().characterId());
        assertEquals(a, snapshot().player2().characterId());
        assertTrue(snapshot().completed());
    }

    @Test
    void impossibleInventoriesAreRejectedBeforePersistingOrScheduling() {
        var onlyOne = new DraftEligibilityProvider.EligibleItems(List.of(a), List.of("BOMBA", "ESCUDO"));
        when(eligibility.forPlayer(p1)).thenReturn(onlyOne);
        when(eligibility.forPlayer(p2)).thenReturn(onlyOne);

        assertThrows(IllegalArgumentException.class, () -> service.start(room, "0042", p1, p2));
        assertTrue(states.find(room).isEmpty());
        assertTrue(published.isEmpty());
        verifyNoInteractions(scheduler, random);
    }

    @Test
    void resumeRearmsExistingStateWithoutResettingItsDeadline() {
        service.start(room, "0042", p1, p2);
        service.resume(room);
        verify(scheduler, times(2)).schedule(any(Runnable.class), eq(Instant.ofEpochMilli(21000)));
        assertEquals(21000, snapshot().deadlineEpochMillis());
        stored.set(null);
        service.expire(room, 21000);
        assertEquals(0, completedCount());
        verifyNoInteractions(random);
    }

    private Long compareAndSet(InvocationOnMock invocation) throws Exception {
        String previous = stored.get();
        long expected = Long.parseLong(invocation.getArgument(2));
        if (previous == null || mapper.readValue(previous, DraftState.class).version() != expected) return 0L;
        return stored.compareAndSet(previous, invocation.getArgument(3)) ? 1L : 0L;
    }

    private DraftState snapshot() {
        return states.find(room).orElseThrow();
    }

    private long completedCount() {
        return published.stream().filter(DraftCompleted.class::isInstance).count();
    }

    private DraftCompleted completedEvent() {
        return published.stream().filter(DraftCompleted.class::isInstance).map(DraftCompleted.class::cast)
                .findFirst().orElseThrow();
    }
}
