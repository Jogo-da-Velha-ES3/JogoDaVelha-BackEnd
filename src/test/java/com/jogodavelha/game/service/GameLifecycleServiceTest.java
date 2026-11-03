package com.jogodavelha.game.service;

import com.jogodavelha.auth.User;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.MatchFinishedEvent;
import com.jogodavelha.game.MatchLifecycleEvent;
import com.jogodavelha.game.RewardResult;
import com.jogodavelha.game.RoundResult;
import com.jogodavelha.game.RoundSkillDraft;
import com.jogodavelha.game.RoundUpdate;
import com.jogodavelha.game.Score;
import com.jogodavelha.game.TurnChangedEvent;
import com.jogodavelha.game.draft.DraftCompleted;
import com.jogodavelha.game.draw.RoundSkillDrawService;
import com.jogodavelha.game.draw.RoundDrawCompleted;
import com.jogodavelha.skill.Skill;
import com.jogodavelha.skill.SkillCategory;
import com.jogodavelha.skill.SkillRepository;
import org.springframework.scheduling.TaskScheduler;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import com.jogodavelha.websocket.WebSocketService;
import com.jogodavelha.websocket.RoomSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameLifecycleServiceTest {

    @Mock private GameService games;
    @Mock private GameRepository gameRepository;
    @Mock private RoomRepository rooms;
    @Mock private GameStateManager states;
    @Mock private TurnService turns;
    @Mock private WebSocketService websocket;
    @Mock private ObjectProvider<RoundSkillDraft> skillDraft;
    @Mock private RoomSnapshotService snapshots;
    @Mock private ApplicationEventPublisher applicationEvents;
    @Mock private RoundSkillDraft roundSkillDraft;
    @Mock private Clock clock;
    @Mock private TaskScheduler scheduler;

    private GameLifecycleService lifecycle;
    private User player1;
    private User player2;
    private Room room;
    private UUID character1Id;
    private UUID character2Id;
    private Game game;
    private final AtomicReference<GameState> redisState = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(clock.millis()).thenReturn(10_000L);
        lenient().when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));
        lifecycle = new GameLifecycleService(games, gameRepository, rooms, states, turns, websocket, skillDraft,
                applicationEvents, clock, scheduler);
        player1 = new User("p1", "pass", "p1@test.com");
        player1.setId(UUID.randomUUID());
        player2 = new User("p2", "pass", "p2@test.com");
        player2.setId(UUID.randomUUID());
        room = new Room("4242", player1);
        setId(room, UUID.randomUUID());
        lenient().when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        room.join(player2);
        character1Id = UUID.randomUUID();
        character2Id = UUID.randomUUID();
        game = new Game(room, player1);
        game.setPlayer2(player2);
        Character c1 = new Character("X", CharacterSymbol.X, 0);
        c1.setId(character1Id);
        Character c2 = new Character("O", CharacterSymbol.O, 0);
        c2.setId(character2Id);
        game.setPlayer1Character(c1);
        game.setPlayer2Character(c2);
        setId(game, UUID.randomUUID());

        lenient().when(skillDraft.getIfAvailable()).thenReturn(roundSkillDraft);
        lenient().doAnswer(invocation -> {
            lifecycle.onRoundDrawCompleted(new RoundDrawCompleted(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(roundSkillDraft).beginRound(any(), anyInt(), any(), any());

        lenient().doAnswer(invocation -> {
            UUID gameId = invocation.getArgument(0);
            UUID p1 = invocation.getArgument(1);
            UUID p2 = invocation.getArgument(2);
            long duration = invocation.getArgument(3);
            SecureRandom fixed = mock(SecureRandom.class);
            when(fixed.nextBoolean()).thenReturn(true);
            GameState created = GameState.createInitial(gameId, p1, p2, duration, fixed);
            redisState.set(created);
            return null;
        }).when(states).create(any(), any(), any(), anyLong());

        lenient().when(states.find(any())).thenAnswer(invocation -> Optional.ofNullable(redisState.get()));
        lenient().doAnswer(invocation -> {
            Function<GameState, GameState> mutator = invocation.getArgument(1);
            GameState current = redisState.get();
            assertNotNull(current);
            redisState.set(mutator.apply(current));
            return null;
        }).when(states).update(any(), any());
        lenient().doAnswer(invocation -> {
            Function<GameState, GameStateManager.Update<Object>> mutator = invocation.getArgument(1);
            var update = mutator.apply(redisState.get());
            redisState.set(update.state());
            return update.result();
        }).when(states).updateWithResult(any(), any());
        lenient().doAnswer(invocation -> {
            redisState.set(null);
            return null;
        }).when(states).delete(any());
    }

    @Test
    void draftCompletedCreatesGamePersistsCharactersAndStartsRoundOne() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id)).thenReturn(game);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));

        lifecycle.onDraftCompleted(draftEvent(false));

        verify(games).startGame(room, character1Id, character2Id);
        verify(states).create(eq(game.getId()), eq(player1.getId()), eq(player2.getId()),
                eq(GameState.DEFAULT_TURN_DURATION_MILLIS));
        verify(roundSkillDraft).beginRound(eq(game.getId()), eq(1), eq(player1.getId()), eq(player2.getId()));
        ArgumentCaptor<MatchLifecycleEvent> events = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, times(3)).sendToTopic(eq("/topic/rooms/4242"), events.capture());
        assertEquals(List.of("PHASE_CHANGE", "ROUND_START", "PHASE_CHANGE"),
                events.getAllValues().stream().map(MatchLifecycleEvent::type).toList());
        assertEquals("VERSUS", events.getAllValues().getFirst().payload().get("phase"));
        assertEquals("PLAYING", events.getAllValues().getLast().payload().get("phase"));
        assertEquals(1, events.getAllValues().getLast().payload().get("round"));
        verify(turns, never()).schedule(any(), anyLong());
        assertEquals(0, redisState.get().getTurnDeadlineEpochMillis());
        ArgumentCaptor<Runnable> presentation = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(presentation.capture(), eq(Instant.ofEpochMilli(12_500)));
        assertEquals(GamePhase.PLAYING, redisState.get().getPhase());

        when(clock.millis()).thenReturn(12_500L);
        presentation.getValue().run();
        presentation.getValue().run();
        ArgumentCaptor<GameState> firstTurn = ArgumentCaptor.forClass(GameState.class);
        verify(turns, times(1)).publishAndSchedule(firstTurn.capture(), eq(Optional.empty()));
        assertEquals(22_500L, firstTurn.getValue().getTurnDeadlineEpochMillis());
    }

    @Test
    void restartedDrawReleasesTurnThroughANewLifecycleInstanceAndKeepsTheNextRoundWorking() {
        var catalog = mock(SkillRepository.class);
        when(catalog.findAll()).thenReturn(List.of("BOMBA", "ESCUDO", "CONGELAR", "TORNADO", "SILENCIO", "AMPULHETA",
                        "BURACO_NEGRO", "CAMPO_DE_FORCA", "TROCA_RAPIDA", "REFORCO")
                .stream().map(code -> new Skill(code, code, SkillCategory.OFFENSE, code, false, true, 0)).toList());
        var clock = mock(Clock.class);
        var now = new java.util.concurrent.atomic.AtomicLong(1_000);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        var draw = new RoundSkillDrawService(states, catalog, rooms, gameRepository, clock,
                scheduler, websocket, snapshots,
                event -> lifecycle.onRoundDrawCompleted((RoundDrawCompleted) event), new Random(1));
        when(skillDraft.getIfAvailable()).thenReturn(draw);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id)).thenReturn(game);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));

        lifecycle.onDraftCompleted(draftEvent(false));

        assertEquals(GamePhase.DRAFT, redisState.get().getPhase());
        verify(turns, never()).schedule(any(), anyLong());
        ArgumentCaptor<MatchLifecycleEvent> openingEvents = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, times(3)).sendToTopic(eq("/topic/rooms/4242"), openingEvents.capture());
        assertEquals(List.of("PHASE_CHANGE", "ROUND_START", "PHASE_CHANGE"),
                openingEvents.getAllValues().stream().map(MatchLifecycleEvent::type).toList());
        assertEquals("VERSUS", openingEvents.getAllValues().getFirst().payload().get("phase"));
        assertEquals(1, openingEvents.getAllValues().get(1).payload().get("round"));
        assertEquals("ROUND_DRAW", openingEvents.getAllValues().getLast().payload().get("phase"));
        lifecycle = new GameLifecycleService(games, gameRepository, rooms, states, turns, websocket, skillDraft,
                applicationEvents, clock, scheduler);
        var restarted = new RoundSkillDrawService(states, catalog, rooms, gameRepository, clock,
                scheduler, websocket, snapshots,
                event -> lifecycle.onRoundDrawCompleted((RoundDrawCompleted) event), new Random(99));
        when(skillDraft.getIfAvailable()).thenReturn(restarted);
        now.set(9_000);
        restarted.resume(game.getId());

        assertEquals(GamePhase.PLAYING, redisState.get().getPhase());
        assertEquals(3, redisState.get().getRoundSkillDecks().get(player1.getId()).size());
        assertEquals(3, redisState.get().getRoundSkillDecks().get(player2.getId()).size());
        assertEquals(0, redisState.get().getConsecutiveTimeouts(player1.getId()));
        assertEquals(0, redisState.get().getConsecutiveTimeouts(player2.getId()));
        verify(turns, never()).schedule(any(), anyLong());
        restarted.expire(game.getId(), 1, 9_000);
        verify(turns, never()).schedule(any(), anyLong());

        when(games.applyRoundResult(game.getId(), 1, RoundResult.PLAYER1_WIN)).thenAnswer(ignored -> {
            game.registerRoundResult(RoundResult.PLAYER1_WIN);
            return new RoundUpdate(game, true);
        });
        lifecycle.finishRound(game.getId(), 1, RoundResult.PLAYER1_WIN);
        assertEquals(GamePhase.DRAFT, redisState.get().getPhase());
        assertEquals(3, redisState.get().getRoundSkillDecks().get(player1.getId()).size());
        assertEquals(3, redisState.get().getRoundSkillDecks().get(player2.getId()).size());
        verify(turns, never()).schedule(any(), anyLong());
        now.set(17_000);
        restarted.expire(game.getId(), 2, 17_000);
        assertEquals(GamePhase.PLAYING, redisState.get().getPhase());
        assertEquals(3, redisState.get().getRoundSkillDecks().get(player1.getId()).size());
        verify(turns, never()).schedule(any(), anyLong());
    }

    @Test
    void automaticDraftCompletedAlsoStartsMatch() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id)).thenReturn(game);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));

        lifecycle.onDraftCompleted(draftEvent(true));

        verify(games).startGame(room, character1Id, character2Id);
        verify(turns, never()).schedule(any(), anyLong());
    }

    @Test
    void duplicateDraftCompletedDoesNotRecreateState() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id)).thenReturn(game);
        redisState.set(GameState.createInitial(game.getId(), player1.getId(), player2.getId(),
                GameState.DEFAULT_TURN_DURATION_MILLIS, new SecureRandom()));

        lifecycle.onDraftCompleted(draftEvent(false));

        verify(states, never()).create(any(), any(), any(), anyLong());
        verify(roundSkillDraft, never()).beginRound(any(), anyInt(), any(), any());
    }

    @Test
    void concurrentDraftCompletedIsIgnoredOnIntegrityViolation() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        lifecycle.onDraftCompleted(draftEvent(false));

        verify(states, never()).create(any(), any(), any(), anyLong());
    }

    @Test
    void missingRoundSkillDraftFailsLoudly() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id)).thenReturn(game);
        when(skillDraft.getIfAvailable()).thenReturn(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> lifecycle.onDraftCompleted(draftEvent(false)));

        assertTrue(error.getMessage().contains("RoundSkillDraft"));
        verify(turns, never()).schedule(any(), anyLong());
    }

    @Test
    void repeatedDrawCompletedEventStartsTurnOnlyOnce() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(games.startGame(room, character1Id, character2Id)).thenReturn(game);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        doNothing().when(roundSkillDraft).beginRound(any(), anyInt(), any(), any());

        lifecycle.onDraftCompleted(draftEvent(false));
        lifecycle.onRoundDrawCompleted(new RoundDrawCompleted(game.getId(), 1));
        lifecycle.onRoundDrawCompleted(new RoundDrawCompleted(game.getId(), 1));

        verify(turns, never()).schedule(any(), anyLong());
        verify(scheduler).schedule(any(Runnable.class), eq(Instant.ofEpochMilli(12_500)));
    }

    @Test
    void twoRoundMatchEndsWithoutSuddenDeathAndPublishesContractualMatchEnd() {
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(1, false);

        when(games.applyRoundResult(game.getId(), 1, RoundResult.PLAYER1_WIN)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.PLAYER1_WIN);
            return new RoundUpdate(game, true);
        });
        when(games.applyRoundResult(game.getId(), 2, RoundResult.PLAYER1_WIN)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.PLAYER1_WIN);
            game.setRewardResult(new RewardResult(GameService.COINS_WIN, GameService.COINS_LOSS));
            return new RoundUpdate(game, true);
        });

        lifecycle.finishRound(game.getId(), 1, RoundResult.PLAYER1_WIN);

        assertEquals(2, game.getCurrentRound());
        assertEquals(GamePhase.PLAYING, redisState.get().getPhase());
        assertEquals(GameState.DEFAULT_TURN_DURATION_MILLIS, redisState.get().getTurnDurationMillis());
        assertEquals(GameState.EMPTY_BOARD, redisState.get().getBoard());
        assertEquals(player2.getId(), redisState.get().getCurrentTurnPlayerId());

        lifecycle.finishRound(game.getId(), 2, RoundResult.PLAYER1_WIN);

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertNull(redisState.get());
        verify(turns, atLeastOnce()).cancel(game.getId());
        assertEquals(RoomStatus.IN_GAME, room.getStatus());
        verify(applicationEvents).publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));

        ArgumentCaptor<MatchLifecycleEvent> events = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, atLeast(3)).sendToTopic(eq("/topic/rooms/4242"), events.capture());
        MatchLifecycleEvent matchEnd = events.getAllValues().stream()
                .filter(e -> "MATCH_END".equals(e.type()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> outcome = (Map<String, Object>) matchEnd.payload().get("outcome");
        assertEquals("WIN", outcome.get("kind"));
        assertEquals("P1", outcome.get("winner"));
        assertEquals(List.of(2, 0), outcome.get("score"));
        assertEquals(false, matchEnd.payload().get("forfeit"));
        assertEquals(GameEndReason.NORMAL.name(), matchEnd.payload().get("reason"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> coins = (Map<String, Integer>) matchEnd.payload().get("coins");
        assertEquals(GameService.COINS_WIN, coins.get("P1"));
        assertEquals(GameService.COINS_LOSS, coins.get("P2"));
        assertFalse(matchEnd.payload().containsKey("winnerId"));
        assertTrue(matchEnd.serverTime() > 0);
    }

    @Test
    void tiedScoreAfterTwoRoundsStartsSuddenDeathWithFiveSecondTurns() {
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(1, false);

        when(games.applyRoundResult(game.getId(), 1, RoundResult.PLAYER1_WIN)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.PLAYER1_WIN);
            return new RoundUpdate(game, true);
        });
        when(games.applyRoundResult(game.getId(), 2, RoundResult.PLAYER2_WIN)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.PLAYER2_WIN);
            return new RoundUpdate(game, true);
        });

        lifecycle.finishRound(game.getId(), 1, RoundResult.PLAYER1_WIN);
        lifecycle.finishRound(game.getId(), 2, RoundResult.PLAYER2_WIN);

        assertEquals(3, game.getCurrentRound());
        assertTrue(game.isSuddenDeath());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS, redisState.get().getTurnDurationMillis());
        assertTrue(redisState.get().isSuddenDeath());
        assertEquals(GamePhase.PLAYING, redisState.get().getPhase());
        verify(websocket, never()).sendToTopic(eq("/topic/rooms/4242"),
                argThat(event -> event instanceof MatchLifecycleEvent m && "MATCH_END".equals(m.type())));
    }

    @Test
    void drawAndVictoryRoundEndsArePublished() {
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(1, false);
        when(games.applyRoundResult(game.getId(), 1, RoundResult.DRAW)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.DRAW);
            return new RoundUpdate(game, true);
        });

        lifecycle.finishRound(game.getId(), 1, RoundResult.DRAW);

        ArgumentCaptor<MatchLifecycleEvent> event = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, atLeastOnce()).sendToTopic(eq("/topic/rooms/4242"), event.capture());
        MatchLifecycleEvent roundEnd = event.getAllValues().stream()
                .filter(e -> "ROUND_END".equals(e.type()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, roundEnd.payload().get("round"));
        assertNull(roundEnd.payload().get("winner"));
        assertFalse(roundEnd.payload().containsKey("winningLine"));
        assertEquals(Map.of("P1", 0, "P2", 0), roundEnd.payload().get("score"));
    }

    @Test
    void victoryRoundEndPublishesTheWinningLineAndUpdatedSlotScore() {
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(1, false);
        redisState.get().setWinningLine(List.of(0, 5, 10, 15));
        when(games.applyRoundResult(game.getId(), 1, RoundResult.PLAYER1_WIN)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.PLAYER1_WIN);
            return new RoundUpdate(game, true);
        });

        lifecycle.finishRound(game.getId(), 1, RoundResult.PLAYER1_WIN);

        ArgumentCaptor<MatchLifecycleEvent> event = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, atLeastOnce()).sendToTopic(eq("/topic/rooms/4242"), event.capture());
        MatchLifecycleEvent roundEnd = event.getAllValues().stream()
                .filter(value -> "ROUND_END".equals(value.type())).findFirst().orElseThrow();
        assertEquals("P1", roundEnd.payload().get("winner"));
        assertEquals(List.of(0, 5, 10, 15), roundEnd.payload().get("winningLine"));
        assertEquals(Map.of("P1", 1, "P2", 0), roundEnd.payload().get("score"));
    }

    @Test
    void playerTwoVictoryAndAbsoluteDrawUseSlotBasedMatchResults() throws Exception {
        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(2, false);
        when(games.applyRoundResult(game.getId(), 2, RoundResult.PLAYER2_WIN)).thenAnswer(invocation -> {
            game.registerRoundResult(RoundResult.PLAYER2_WIN);
            game.setRewardResult(new RewardResult(GameService.COINS_LOSS, GameService.COINS_WIN));
            return new RoundUpdate(game, true);
        });

        lifecycle.finishRound(game.getId(), 2, RoundResult.PLAYER2_WIN);

        ArgumentCaptor<MatchLifecycleEvent> events = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, atLeastOnce()).sendToTopic(eq("/topic/rooms/4242"), events.capture());
        MatchLifecycleEvent matchEnd = events.getAllValues().stream()
                .filter(value -> "MATCH_END".equals(value.type())).findFirst().orElseThrow();
        assertEquals(Map.of("kind", "WIN", "winner", "P2", "score", List.of(0, 2)),
                matchEnd.payload().get("outcome"));
        assertEquals(Map.of("P1", GameService.COINS_LOSS, "P2", GameService.COINS_WIN),
                matchEnd.payload().get("coins"));

        Game drawGame = new Game(room, player1);
        drawGame.setPlayer2(player2);
        setId(drawGame, UUID.randomUUID());
        drawGame.registerRoundResult(RoundResult.DRAW);
        drawGame.registerRoundResult(RoundResult.DRAW);
        when(gameRepository.findById(drawGame.getId())).thenReturn(Optional.of(drawGame));
        redisState.set(GameState.createInitial(drawGame.getId(), player1.getId(), player2.getId(),
                GameState.DEFAULT_TURN_DURATION_MILLIS, new SecureRandom()));
        redisState.get().startRound(3, GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS, 10_000);
        when(games.applyRoundResult(drawGame.getId(), 3, RoundResult.DRAW)).thenAnswer(invocation -> {
            drawGame.registerRoundResult(RoundResult.DRAW);
            drawGame.setRewardResult(new RewardResult(GameService.COINS_DRAW, GameService.COINS_DRAW));
            return new RoundUpdate(drawGame, true);
        });

        lifecycle.finishRound(drawGame.getId(), 3, RoundResult.DRAW);

        verify(websocket, atLeastOnce()).sendToTopic(eq("/topic/rooms/4242"), events.capture());
        MatchLifecycleEvent drawEnd = events.getAllValues().stream()
                .filter(value -> "MATCH_END".equals(value.type())).reduce((first, second) -> second).orElseThrow();
        assertEquals(Map.of("kind", "ABSOLUTE_DRAW"), drawEnd.payload().get("outcome"));
        assertEquals(Map.of("P1", GameService.COINS_DRAW, "P2", GameService.COINS_DRAW),
                drawEnd.payload().get("coins"));
    }

    @Test
    void forfeitPublishesMatchEndOnceAndClearsRedis() {
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(1, false);
        redisState.get().recordTimeout(player1.getId());
        redisState.get().recordTimeout(player1.getId());
        redisState.get().recordTimeout(player1.getId());

        when(games.forfeit(game.getId(), player1)).thenAnswer(invocation -> {
            game.forfeit(player1);
            game.setRewardResult(new RewardResult(0, GameService.COINS_FORFEIT_WIN));
            game.markRewardsGranted();
            return game;
        });

        TurnChangedEvent timeout = new TurnChangedEvent(game.getId(), player2.getId(),
                System.currentTimeMillis(), 1, false, false, null, player1.getId());
        lifecycle.onTurnChanged(timeout);
        lifecycle.onTurnChanged(timeout);

        verify(games, times(1)).forfeit(game.getId(), player1);
        verify(states, atLeastOnce()).delete(game.getId());
        verify(applicationEvents, times(1)).publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));
        org.mockito.InOrder publicationOrder = inOrder(applicationEvents, states);
        publicationOrder.verify(applicationEvents).publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));
        publicationOrder.verify(states, times(2)).delete(game.getId());
        ArgumentCaptor<MatchLifecycleEvent> events = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, atLeastOnce()).sendToTopic(eq("/topic/rooms/4242"), events.capture());
        MatchLifecycleEvent matchEnd = events.getAllValues().stream()
                .filter(event -> "MATCH_END".equals(event.type())).findFirst().orElseThrow();
        assertEquals("MATCH_END", matchEnd.type());
        assertEquals(true, matchEnd.payload().get("forfeit"));
        assertEquals(GameEndReason.FORFEIT_TIMEOUT.name(), matchEnd.payload().get("reason"));
        @SuppressWarnings("unchecked")
        Map<String, Object> outcome = (Map<String, Object>) matchEnd.payload().get("outcome");
        assertEquals(Map.of("kind", "WO", "winner", "P2"), outcome);
        @SuppressWarnings("unchecked")
        Map<String, Integer> coins = (Map<String, Integer>) matchEnd.payload().get("coins");
        assertEquals(GameService.COINS_FORFEIT_WIN, coins.get("P2"));
        assertEquals(0, coins.get("P1"));
        assertFalse(matchEnd.payload().toString().contains(player1.getId().toString()));
        assertFalse(matchEnd.payload().toString().contains(player2.getId().toString()));
        assertEquals(GameEndReason.FORFEIT_TIMEOUT, game.getEndReason());
        assertEquals(RoomStatus.IN_GAME, room.getStatus());
        verify(applicationEvents, times(1)).publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));
    }

    @Test
    void repeatedFinishMatchDoesNotRepublishMatchEnd() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.setRewardResult(new RewardResult(GameService.COINS_WIN, GameService.COINS_LOSS));
        when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        seedPlayingState(2, false);

        lifecycle.finishRound(game.getId(), 2, RoundResult.PLAYER1_WIN);
        lifecycle.finishRound(game.getId(), 2, RoundResult.PLAYER1_WIN);

        verify(websocket, times(1)).sendToTopic(eq("/topic/rooms/4242"),
                argThat(event -> event instanceof MatchLifecycleEvent m && "MATCH_END".equals(m.type())));
        verify(states, atLeastOnce()).delete(game.getId());
        verify(applicationEvents, times(1)).publishEvent(new MatchFinishedEvent(game.getId(), room.getCode()));
    }

    @Test
    void voluntaryForfeitUsesTheSameFinalizationAndRewardResult() {
        game.forfeit(player1);
        game.setRewardResult(new RewardResult(0, GameService.COINS_FORFEIT_WIN));
        game.markRewardsGranted();
        when(games.forfeit(game.getId(), player1.getId())).thenReturn(game);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        seedPlayingState(1, false);

        lifecycle.forfeitActiveGame(game.getId(), player1.getId());

        verify(games).forfeit(game.getId(), player1.getId());
        ArgumentCaptor<MatchLifecycleEvent> events = ArgumentCaptor.forClass(MatchLifecycleEvent.class);
        verify(websocket, atLeastOnce()).sendToTopic(eq("/topic/rooms/4242"), events.capture());
        MatchLifecycleEvent matchEnd = events.getAllValues().stream()
                .filter(event -> "MATCH_END".equals(event.type())).findFirst().orElseThrow();
        assertEquals(Map.of("kind", "WO", "winner", "P2"), matchEnd.payload().get("outcome"));
        assertEquals(Map.of("P1", 0, "P2", GameService.COINS_FORFEIT_WIN), matchEnd.payload().get("coins"));
    }

    private DraftCompleted draftEvent(boolean automatic) {
        return new DraftCompleted(room.getId(), room.getCode(), List.of(
                new DraftCompleted.Selection(player1.getId(), character1Id, List.of("BOMBA", "ESCUDO")),
                new DraftCompleted.Selection(player2.getId(), character2Id, List.of("TORNADO", "SILENCIO"))
        ), automatic);
    }

    private void seedPlayingState(int round, boolean suddenDeath) {
        SecureRandom fixed = mock(SecureRandom.class);
        when(fixed.nextBoolean()).thenReturn(true);
        GameState state = GameState.createInitial(game.getId(), player1.getId(), player2.getId(),
                suddenDeath ? GameState.SUDDEN_DEATH_TURN_DURATION_MILLIS : GameState.DEFAULT_TURN_DURATION_MILLIS,
                fixed);
        if (round != 1) {
            state.startRound(round, state.getTurnDurationMillis(), System.currentTimeMillis());
        }
        state.setPhase(GamePhase.PLAYING);
        redisState.set(state);
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
