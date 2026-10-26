package com.jogodavelha.game.draw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.MatchLifecycleEvent;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.skill.Skill;
import com.jogodavelha.skill.SkillCategory;
import com.jogodavelha.skill.SkillRepository;
import com.jogodavelha.websocket.RoomSnapshotService;
import com.jogodavelha.websocket.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoundSkillDrawServiceTest {
    private final UUID gameId = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> stored = new HashMap<>();
    private final SkillRepository skills = mock(SkillRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final RoomSnapshotService snapshots = mock(RoomSnapshotService.class);
    private final Runnable completed = mock(Runnable.class);
    private final AtomicLong now = new AtomicLong(1_000);
    private final AtomicBoolean failNextCas = new AtomicBoolean();
    private Runnable onConflict = () -> { };
    private GameStateManager states;
    private RoundSkillDrawService draws;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var redis = mock(RedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenAnswer(call -> stored.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> {
                    if (failNextCas.getAndSet(false)) {
                        onConflict.run();
                        return false;
                    }
                    String key = ((List<String>) call.getArgument(1)).getFirst();
                    String before = stored.get(key);
                    if (before == null || mapper.readTree(before).path("version").asLong()
                            != Long.parseLong(call.getArgument(2))) return false;
                    stored.put(key, call.getArgument(3));
                    return true;
                });
        states = new GameStateManager(redis, mapper, 1_800_000, 5, new SecureRandom());
        states.create(gameId, p1, p2, 10_000);
        states.update(gameId, state -> {
            state.setPhase(GamePhase.DRAFT);
            state.setInitialSkills(p1, List.of("BOMBA", "ESCUDO"));
            state.setInitialSkills(p2, List.of("CONGELAR", "SILENCIO"));
            return state;
        });
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        var game = mock(Game.class);
        var room = mock(Room.class);
        when(room.getCode()).thenReturn("0042");
        when(room.getId()).thenReturn(new UUID(0, 42));
        when(game.getId()).thenReturn(gameId);
        when(game.getRoom()).thenReturn(room);
        when(games.findById(gameId)).thenReturn(Optional.of(game));
        when(games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)).thenReturn(Optional.of(game));
        when(rooms.findActiveMembership(eq("0042"), anyList(), eq(p1))).thenReturn(Optional.of(room));
        when(rooms.findActiveMembership(eq("0042"), anyList(), eq(p2))).thenReturn(Optional.of(room));
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        when(skills.findAll()).thenReturn(List.of("BOMBA", "ESCUDO", "CONGELAR", "SILENCIO", "TORNADO", "AMPULHETA")
                .stream().map(code -> new Skill(code, code, SkillCategory.OFFENSE, code, false, true, 50)).toList());
        draws = new RoundSkillDrawService(states, skills, rooms, games, clock, scheduler, websocket, snapshots, new Random(7));
    }

    @Test
    void drawsThreeDistinctOptionsFromTheWholeCatalogAndSendsThemOnlyToTheirOwner() {
        start();
        var state = state();
        assertEquals(9_000, state.getRoundDraw().deadline());
        assertEquals(GamePhase.DRAFT, state.getPhase());
        assertTrue(state.getRoundSkillDecks().isEmpty());
        for (UUID player : List.of(p1, p2)) {
            var options = state.getRoundDraw().players().get(player).options();
            assertEquals(3, new HashSet<>(options).size());
            assertTrue(Collections.disjoint(options, state.getInitialSkillsByPlayer().get(player)));
            verify(websocket).sendToUser(eq(player.toString()), eq("/queue/room"), argThat(value ->
                    value instanceof MatchLifecycleEvent event && event.type().equals("DRAW_OPTIONS")
                            && event.payload().get("options").equals(options)));
        }
        verify(websocket, never()).sendToTopic(anyString(), argThat(value ->
                value instanceof MatchLifecycleEvent event && event.type().equals("DRAW_OPTIONS")));
        verifyNoInteractions(completed);
    }

    @Test
    void keepsConfirmedChoicesAndCompletesOnlyWhenBothPlayersHaveADeck() {
        start();
        String first = option(p1), second = option(p2);
        draws.pick("0042", p1, first);
        verifyNoInteractions(completed);
        assertEquals(Set.of("BOMBA", "ESCUDO", first), state().getRoundSkillDecks().get(p1));
        assertThrows(IllegalStateException.class, () -> draws.pick("0042", p1, first));
        draws.pick("0042", p2, second);
        assertEquals(Set.of("CONGELAR", "SILENCIO", second), state().getRoundSkillDecks().get(p2));
        verify(completed).run();
        draws.expire(gameId, 1, 9_000);
        verify(completed, times(1)).run();
    }

    @Test
    void timeoutPicksOnlyForTheMissingPlayerWithoutCountingAForfeitTimeout() {
        start();
        String selected = option(p1);
        draws.pick("0042", p1, selected);
        now.set(9_000);
        draws.expire(gameId, 1, 9_000);
        var state = state();
        assertEquals(selected, state.getRoundDraw().players().get(p1).selected());
        var second = state.getRoundDraw().players().get(p2);
        assertTrue(second.options().contains(second.selected()));
        assertEquals(3, state.getRoundSkillDecks().get(p2).size());
        assertEquals(0, state.getConsecutiveTimeouts(p1));
        assertEquals(0, state.getConsecutiveTimeouts(p2));
        verify(completed).run();
    }

    @Test
    void earlyTimerDoesNotResolveTheDrawAndLateManualChoiceIsRejected() {
        start();
        draws.expire(gameId, 1, 9_000);
        assertFalse(state().getRoundDraw().completed());
        now.set(9_000);
        assertThrows(IllegalStateException.class, () -> draws.pick("0042", p1, option(p1)));
        assertFalse(state().getRoundDraw().completed());
        verifyNoInteractions(completed);
    }

    @Test
    void rejectsNonParticipantsAndChoicesOutsideTheOptions() {
        start();
        assertThrows(AccessDeniedException.class, () -> draws.pick("0042", UUID.randomUUID(), "TORNADO"));
        assertThrows(IllegalArgumentException.class, () -> draws.pick("0042", p1, "BOMBA"));
        assertThrows(IllegalArgumentException.class, () -> draws.pick("0042", p1, " "));
        assertThrows(IllegalArgumentException.class, () -> draws.pick("bad", p1, "TORNADO"));
        assertTrue(state().getRoundSkillDecks().isEmpty());
    }

    @Test
    void repeatedBeginAndRedisRetriesKeepTheSameOptionsAndDeadline() {
        failNextCas.set(true);
        start();
        var original = state().getRoundDraw();
        now.set(2_000);
        start();
        assertEquals(original, state().getRoundDraw());
        failNextCas.set(true);
        draws.pick("0042", p1, option(p1));
        assertEquals(original.players().get(p1).options(), state().getRoundDraw().players().get(p1).options());
        verifyNoInteractions(completed);
    }

    @Test
    void nextRoundRenewsDeckAndUsageWhilePreservingFixedSkillsAndTimeoutCounters() {
        start();
        now.set(9_000);
        draws.expire(gameId, 1, 9_000);
        states.update(gameId, state -> {
            state.recordSkillUse(p1, "BOMBA");
            state.recordTimeout(p1);
            state.startRound(2, 10_000, 9_100);
            state.setPhase(GamePhase.DRAFT);
            return state;
        });
        Runnable nextRound = mock(Runnable.class);
        draws.beginRound(gameId, 2, p1, p2, nextRound);
        draws.expire(gameId, 1, 9_000);
        assertTrue(state().getRoundSkillDecks().isEmpty());
        assertFalse(state().hasUsedSkill(p1, "BOMBA"));
        assertEquals(1, state().getConsecutiveTimeouts(p1));
        now.set(17_000);
        draws.expire(gameId, 2, 17_000);
        assertEquals(3, state().getRoundSkillDecks().get(p1).size());
        assertTrue(state().hasSkillInRoundDeck(p1, "BOMBA"));
        verify(nextRound).run();
    }

    @Test
    void duplicateTimeoutAndRedisRetryDoNotRunCompletionTwice() {
        start();
        failNextCas.set(true);
        now.set(9_000);
        draws.expire(gameId, 1, 9_000);
        var selected = state().getRoundDraw();
        draws.expire(gameId, 1, 9_000);
        assertEquals(selected, state().getRoundDraw());
        verify(completed, times(1)).run();
    }

    @Test
    void timerForADeletedGameDoesNothing() {
        start();
        stored.clear();
        now.set(9_000);
        draws.expire(gameId, 1, 9_000);
        verifyNoInteractions(completed);
    }

    @Test
    void injectedGeneratorMakesOptionsAndAutomaticChoiceDeterministic() {
        var random = mock(java.util.random.RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(0);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        draws = new RoundSkillDrawService(states, skills, rooms, games, clock, scheduler, websocket, snapshots, random);
        start();
        assertEquals(List.of("AMPULHETA", "CONGELAR", "SILENCIO"), state().getRoundDraw().players().get(p1).options());
        assertEquals(List.of("AMPULHETA", "BOMBA", "ESCUDO"), state().getRoundDraw().players().get(p2).options());
        now.set(9_000);
        draws.expire(gameId, 1, 9_000);
        assertEquals("AMPULHETA", state().getRoundDraw().players().get(p1).selected());
        assertEquals("AMPULHETA", state().getRoundDraw().players().get(p2).selected());
    }

    @Test
    void retryAfterTheDeadlineRejectsTheChoiceInsteadOfUsingTheOldRequestTime() {
        start();
        now.set(8_999);
        failNextCas.set(true);
        onConflict = () -> now.set(9_000);
        assertThrows(IllegalStateException.class, () -> draws.pick("0042", p1, option(p1)));
        assertNull(state().getRoundDraw().players().get(p1).selected());
        assertTrue(state().getRoundSkillDecks().isEmpty());
    }

    @Test
    void aConcurrentOpponentsChoiceIsPreservedWhenTheUpdateRetries() {
        start();
        String first = option(p1), second = option(p2);
        failNextCas.set(true);
        onConflict = () -> draws.pick("0042", p2, second);
        draws.pick("0042", p1, first);
        assertEquals(first, state().getRoundDraw().players().get(p1).selected());
        assertEquals(second, state().getRoundDraw().players().get(p2).selected());
        verify(completed, times(1)).run();
    }

    private void start() { draws.beginRound(gameId, 1, p1, p2, completed); }
    private GameState state() { return states.find(gameId).orElseThrow(); }
    private String option(UUID player) { return state().getRoundDraw().players().get(player).options().getFirst(); }
}
