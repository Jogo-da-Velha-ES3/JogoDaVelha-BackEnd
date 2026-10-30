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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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
    private final ApplicationEventPublisher completed = mock(ApplicationEventPublisher.class);
    private Clock clock;
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
        when(redis.scan(any(ScanOptions.class))).thenAnswer(ignored -> {
            var iterator = List.copyOf(stored.keySet()).iterator();
            var cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenAnswer(call -> iterator.hasNext());
            when(cursor.next()).thenAnswer(call -> iterator.next());
            return cursor;
        });
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
        clock = mock(Clock.class);
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
        when(skills.findAll()).thenReturn(List.of("BOMBA", "ESCUDO", "CONGELAR", "SILENCIO", "TORNADO", "AMPULHETA",
                        "BURACO_NEGRO", "CAMPO_DE_FORCA", "TROCA_RAPIDA", "REFORCO")
                .stream().map(code -> new Skill(code, code, SkillCategory.OFFENSE, code, false, true, 50)).toList());
        doAnswer(call -> {
            states.update(gameId, state -> { state.setPhase(GamePhase.PLAYING); return state; });
            return null;
        }).when(completed).publishEvent(isA(RoundDrawCompleted.class));
        draws = new RoundSkillDrawService(states, skills, rooms, games, clock, scheduler, websocket, snapshots, completed, new Random(7));
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
    void insufficientEligibleCatalogFailsBeforeSavingAnyDrawOrDeckChange() {
        when(skills.findAll()).thenReturn(List.of("BOMBA", "ESCUDO", "CONGELAR", "SILENCIO")
                .stream().map(code -> new Skill(code, code, SkillCategory.OFFENSE, code, false, true, 50)).toList());
        assertThrows(IllegalStateException.class, this::start);
        assertNull(state().getRoundDraw());
        assertTrue(state().getRoundSkillDecks().isEmpty());
        verifyNoInteractions(websocket, snapshots, completed);
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
        verify(completed).publishEvent(new RoundDrawCompleted(gameId, 1));
        draws.expire(gameId, 1, 9_000);
        verify(completed, times(1)).publishEvent(new RoundDrawCompleted(gameId, 1));
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
        verify(completed).publishEvent(new RoundDrawCompleted(gameId, 1));
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
        draws.beginRound(gameId, 2, p1, p2);
        var previous = Set.copyOf(state().getRoundSkillDecks().get(p1));
        assertEquals(3, previous.size());
        assertTrue(Collections.disjoint(previous, state().getRoundDraw().players().get(p1).options()));
        draws.expire(gameId, 1, 9_000);
        assertEquals(previous, state().getRoundSkillDecks().get(p1));
        assertFalse(state().hasUsedSkill(p1, "BOMBA"));
        assertEquals(1, state().getConsecutiveTimeouts(p1));
        now.set(17_000);
        draws.expire(gameId, 2, 17_000);
        assertEquals(3, state().getRoundSkillDecks().get(p1).size());
        assertEquals(2, state().getRoundSkillDecks().get(p1).stream().filter(previous::contains).count());
        verify(completed).publishEvent(new RoundDrawCompleted(gameId, 2));
    }

    @Test
    void laterManualDrawReplacesOneOfAllCurrentSkillsAndLeavesOpponentDeckUntouched() {
        start();
        draws.pick("0042", p1, option(p1));
        draws.pick("0042", p2, option(p2));
        var p1Deck = Set.copyOf(state().getRoundSkillDecks().get(p1));
        var p2Deck = Set.copyOf(state().getRoundSkillDecks().get(p2));
        states.update(gameId, state -> { state.startRound(2, 10_000, 9_100); state.setPhase(GamePhase.DRAFT); return state; });
        now.set(10_000);
        draws.beginRound(gameId, 2, p1, p2);
        var options = state().getRoundDraw().players().get(p1).options();
        assertTrue(Collections.disjoint(p1Deck, options));
        String add = options.getFirst();
        draws.pick("0042", p1, add);
        Set<String> updated = state().getRoundSkillDecks().get(p1);
        assertEquals(3, updated.size());
        assertTrue(updated.contains(add));
        assertEquals(2, updated.stream().filter(p1Deck::contains).count());
        assertEquals(p2Deck, state().getRoundSkillDecks().get(p2));
    }

    @Test
    void laterOptionsCanRepeatUnchosenOffersButExcludeChosenSkillsOnlyForTheirOwner() {
        states.update(gameId, state -> {
            state.setInitialSkills(p1, List.of("BOMBA", "ESCUDO"));
            state.setInitialSkills(p2, List.of("CONGELAR", "SILENCIO"));
            state.setRoundSkillDeck(p1, List.of("BOMBA", "ESCUDO", "AMPULHETA"));
            state.setRoundSkillDeck(p2, List.of("CONGELAR", "SILENCIO", "AMPULHETA"));
            state.recordChosenDrawSkill(p1, "TORNADO"); // escolhido anteriormente, agora fora do deck
            state.startRound(2, 10_000, 1_000);
            state.setPhase(GamePhase.DRAFT);
            return state;
        });
        var deterministic = mock(java.util.random.RandomGenerator.class);
        when(deterministic.nextInt(anyInt())).thenReturn(0);
        draws = new RoundSkillDrawService(states, skills, rooms, games, clock, scheduler,
                websocket, snapshots, completed, deterministic);

        draws.beginRound(gameId, 2, p1, p2);

        var p1Options = state().getRoundDraw().players().get(p1).options();
        var p2Options = state().getRoundDraw().players().get(p2).options();
        assertFalse(p1Options.contains("TORNADO"));
        assertTrue(p1Options.contains("BURACO_NEGRO"));
        assertFalse(state().getChosenDrawSkills(p1).contains("BURACO_NEGRO"));
        assertTrue(p2Options.contains("BURACO_NEGRO"));
        assertTrue(state().getChosenDrawSkills(p1).containsAll(List.of("BOMBA", "ESCUDO", "TORNADO")));
        assertFalse(state().getChosenDrawSkills(p2).contains("TORNADO"));
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
        verify(completed, times(1)).publishEvent(new RoundDrawCompleted(gameId, 1));
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
        when(skills.findAll()).thenReturn(List.of("BOMBA", "ESCUDO", "CONGELAR", "SILENCIO", "TORNADO", "AMPULHETA")
                .stream().map(code -> new Skill(code, code, SkillCategory.OFFENSE, code, false, true, 50)).toList());
        var random = mock(java.util.random.RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(0);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        draws = new RoundSkillDrawService(states, skills, rooms, games, clock, scheduler, websocket, snapshots, completed, random);
        start();
        assertEquals(List.of("AMPULHETA", "CONGELAR", "SILENCIO"), state().getRoundDraw().players().get(p1).options());
        assertEquals(List.of("AMPULHETA", "BOMBA", "ESCUDO"), state().getRoundDraw().players().get(p2).options());
        now.set(9_000);
        draws.expire(gameId, 1, 9_000);
        assertEquals("AMPULHETA", state().getRoundDraw().players().get(p1).selected());
        assertEquals("AMPULHETA", state().getRoundDraw().players().get(p2).selected());
        assertTrue(state().getChosenDrawSkills(p1).contains("AMPULHETA"));
        assertTrue(state().getChosenDrawSkills(p2).contains("AMPULHETA"));
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
        verify(completed, times(1)).publishEvent(new RoundDrawCompleted(gameId, 1));
    }

    private void start() { draws.beginRound(gameId, 1, p1, p2); }

    @Test
    void restartRearmsTheOriginalDeadlineAndResolvesWithoutThePreviousServiceInstance() {
        start();
        var original = state().getRoundDraw();
        now.set(4_000);
        clearInvocations(scheduler, completed, skills);
        draws = restartedService();

        draws.resumePendingRounds();

        var timer = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(timer.capture(), eq(Instant.ofEpochMilli(9_000)));
        assertEquals(original, state().getRoundDraw());
        verifyNoInteractions(skills, completed);
        now.set(9_000);
        timer.getValue().run();
        assertEquals(GamePhase.PLAYING, state().getPhase());
        assertEquals(3, state().getRoundSkillDecks().get(p1).size());
        verify(completed).publishEvent(new RoundDrawCompleted(gameId, 1));
    }

    @Test
    void restartAfterExpiryKeepsTheManualChoiceAndResolvesOnlyTheMissingOne() {
        start();
        String selected = option(p1);
        draws.pick("0042", p1, selected);
        clearInvocations(websocket);
        now.set(10_000);

        restartedService().resumePendingRounds();

        assertEquals(selected, state().getRoundDraw().players().get(p1).selected());
        assertTrue(state().getRoundDraw().completed());
        assertEquals(GamePhase.PLAYING, state().getPhase());
        verify(websocket, never()).sendToUser(eq(p1.toString()), anyString(), any());
        verify(websocket).sendToUser(eq(p2.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
        verify(completed).publishEvent(new RoundDrawCompleted(gameId, 1));
    }

    @Test
    void restartReplaysCompletionIfTheProcessStoppedAfterSavingBothChoices() {
        start();
        states.update(gameId, state -> {
            var draw = state.getRoundDraw();
            for (UUID player : List.of(p1, p2)) {
                String selected = draw.players().get(player).options().getFirst();
                draw = draw.select(player, selected);
                var deck = new HashSet<>(state.getInitialSkillsByPlayer().get(player));
                deck.add(selected);
                state.setRoundSkillDeck(player, deck);
            }
            state.setRoundDraw(draw);
            return state;
        });
        clearInvocations(skills, scheduler, websocket);

        restartedService().resumePendingRounds();

        assertEquals(GamePhase.PLAYING, state().getPhase());
        verify(completed).publishEvent(new RoundDrawCompleted(gameId, 1));
        verifyNoInteractions(skills, scheduler, websocket);
    }

    @Test
    void eachManualChoicePublishesOneDeckAndFirstChoicePublishesAnUpdatedSnapshot() {
        start();
        draws.pick("0042", p1, option(p1));
        verify(snapshots, times(2)).publish("0042");
        draws.pick("0042", p2, option(p2));

        verify(websocket, times(1)).sendToUser(eq(p1.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
        verify(websocket, times(1)).sendToUser(eq(p2.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
    }

    @Test
    void onlyTheWinningTimeoutAttemptPublishesDecksWhenTwoExpirationsOverlap() {
        start();
        now.set(9_000);
        failNextCas.set(true);
        onConflict = () -> draws.expire(gameId, 1, 9_000);

        draws.expire(gameId, 1, 9_000);

        verify(websocket, times(1)).sendToUser(eq(p1.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
        verify(websocket, times(1)).sendToUser(eq(p2.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
        verify(completed, times(1)).publishEvent(new RoundDrawCompleted(gameId, 1));
    }

    @Test
    void timeoutRacingAnInFlightManualPickCommitsOnlyTheAutomaticDeckChange() {
        start();
        String manualRequest = option(p1);
        now.set(8_999);
        failNextCas.set(true);
        // The manual request passes its deadline check, then the timeout commits before its CAS retry.
        onConflict = () -> {
            now.set(9_000);
            draws.expire(gameId, 1, 9_000);
        };

        assertThrows(IllegalStateException.class, () -> draws.pick("0042", p1, manualRequest));

        var state = state();
        String selected = state.getRoundDraw().players().get(p1).selected();
        assertTrue(state.getRoundDraw().players().get(p1).options().contains(selected));
        assertEquals(Set.of("BOMBA", "ESCUDO", selected), state.getRoundSkillDecks().get(p1));
        assertEquals(3, state.getRoundSkillDecks().get(p1).size());
        assertEquals(3, state.getRoundSkillDecks().get(p2).size());
        assertEquals(0, state.getConsecutiveTimeouts(p1));
        assertEquals(0, state.getConsecutiveTimeouts(p2));
        verify(websocket, times(1)).sendToUser(eq(p1.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
        verify(websocket, times(1)).sendToUser(eq(p2.toString()), eq("/queue/room"), argThat(this::isDeckUpdate));
        verify(completed, times(1)).publishEvent(new RoundDrawCompleted(gameId, 1));
    }

    private RoundSkillDrawService restartedService() {
        return new RoundSkillDrawService(states, skills, rooms, games, clock, scheduler,
                websocket, snapshots, completed, new Random(99));
    }

    private boolean isDeckUpdate(Object value) {
        return value instanceof MatchLifecycleEvent event && event.type().equals("DECK_UPDATE");
    }
    private GameState state() { return states.find(gameId).orElseThrow(); }
    private String option(UUID player) { return state().getRoundDraw().players().get(player).options().getFirst(); }
}
