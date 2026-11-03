package com.jogodavelha.game.service;

import com.jogodavelha.auth.User;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.coins.CoinTransactionService;
import com.jogodavelha.game.*;
import com.jogodavelha.game.logic.*;
import com.jogodavelha.profile.UserStatsService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.skill.HourglassEffect;
import com.jogodavelha.skill.SkillActivationService;
import com.jogodavelha.skill.SkillContext;
import com.jogodavelha.skill.SkillEngine;
import com.jogodavelha.websocket.WebSocketService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InactivityForfeitTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("unchecked")
    void thirdTimeoutAcrossRoundsEndsTheWholeMatchPaysOnlyTheOpponentAndDoesNotRestartTheTimer(boolean hostLoses) {
        User p1 = player("host"), p2 = player("guest");
        User loser = hostLoses ? p1 : p2, winner = hostLoses ? p2 : p1;
        Room room = new Room("0042", p1);
        room.setId(UUID.randomUUID());
        room.join(p2);
        Game game = new Game(room, p1);
        game.setPlayer2(p2);
        UUID gameId = UUID.randomUUID();
        ReflectionTestUtils.setField(game, "id", gameId);
        var repository = mock(GameRepository.class);
        when(repository.findById(gameId)).thenReturn(Optional.of(game));
        when(repository.findByIdForUpdate(gameId)).thenReturn(Optional.of(game));
        when(repository.save(game)).thenReturn(game);
        var rooms = mock(RoomRepository.class);
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        var coins = mock(CoinTransactionService.class);
        var games = new GameService(repository, mock(CharacterRepository.class), coins, mock(UserStatsService.class));
        var states = mock(GameStateManager.class);
        var stored = new AtomicReference<>(GameState.createInitial(gameId, p1.getId(), p2.getId(), 10_000, new SecureRandom()));
        stored.get().setCurrentTurnPlayerId(loser.getId());
        var now = new AtomicLong(stored.get().getTurnDeadlineEpochMillis());
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(ignored -> now.get());
        when(states.find(gameId)).thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        doAnswer(call -> {
            if (stored.get() == null) throw new GameStateNotFoundException(gameId);
            Function<GameState, GameState> update = call.getArgument(1);
            stored.set(update.apply(stored.get()));
            return null;
        }).when(states).update(eq(gameId), any());
        doAnswer(ignored -> { stored.set(null); return null; }).when(states).delete(gameId);
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        var websocket = mock(WebSocketService.class);
        var applicationEvents = mock(ApplicationEventPublisher.class);
        var lifecycle = new AtomicReference<GameLifecycleService>();
        var engine = new SkillEngine(List.of(new HourglassEffect()), new GameValidator());
        var turns = new TurnService(states, engine, clock, scheduler,
                event -> lifecycle.get().onTurnChanged((TurnChangedEvent) event));
        lifecycle.set(new GameLifecycleService(games, repository, rooms, states, turns, websocket,
                mock(ObjectProvider.class), applicationEvents));
        var moves = new MoveActionService(states, new MoveValidator(clock), turns, clock);

        for (int timeout = 0; timeout < 2; timeout++) {
            now.set(stored.get().getTurnDeadlineEpochMillis());
            turns.expire(gameId, now.get());
            assertEquals(timeout + 1, stored.get().getConsecutiveTimeouts(loser.getId()));
            moves.play(gameId, winner.getId(), timeout);
        }
        game.registerRoundResult(RoundResult.DRAW);
        stored.get().startRound(2, 10_000, now.get());
        stored.get().setCurrentTurnPlayerId(loser.getId());
        assertEquals(2, stored.get().getConsecutiveTimeouts(loser.getId()));
        clearInvocations(scheduler);
        now.set(stored.get().getTurnDeadlineEpochMillis());

        turns.expire(gameId, now.get());
        turns.expire(gameId, now.get());

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(GameEndReason.FORFEIT_TIMEOUT, game.getEndReason());
        assertEquals(winner.getId(), game.getWinner().getId());
        assertEquals(30, winner.getCoinsBalance());
        assertEquals(0, loser.getCoinsBalance());
        assertNull(stored.get());
        verify(coins, times(1)).registerCredit(winner, 30, "MATCH_FORFEIT_WIN");
        verify(coins, never()).registerCredit(eq(loser), anyInt(), anyString());
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        verify(websocket, times(1)).sendToTopic(eq("/topic/rooms/0042"), argThat(value ->
                value instanceof MatchLifecycleEvent event && event.type().equals("MATCH_END")
                        && Boolean.TRUE.equals(event.payload().get("forfeit"))));
        verify(applicationEvents).publishEvent(new MatchFinishedEvent(gameId, room.getCode()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validMoveOrAbilityResetsOnlyTheActingPlayersCounter(boolean ability) {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), gameId = UUID.randomUUID();
        var state = GameState.createInitial(gameId, p1, p2, 10_000, new SecureRandom());
        state.setCurrentTurnPlayerId(p1);
        state.recordTimeout(p1);
        state.recordTimeout(p1);
        state.recordTimeout(p2);
        state.setRoundSkillDeck(p1, List.of("AMPULHETA", "BOMBA", "ESCUDO"));
        var states = mock(GameStateManager.class);
        doAnswer(call -> {
            Function<GameState, GameState> update = call.getArgument(1);
            update.apply(state);
            return null;
        }).when(states).update(eq(gameId), any());
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        var clock = Clock.fixed(Instant.ofEpochMilli(1_000), java.time.ZoneOffset.UTC);
        var engine = new SkillEngine(List.of(new HourglassEffect()), new GameValidator());
        var turns = new TurnService(states, engine, clock, scheduler, ignored -> { });

        if (ability) new SkillActivationService(states, engine, turns, clock)
                .activate(gameId, "AMPULHETA", new SkillContext(p1, null));
        else new MoveActionService(states, new MoveValidator(clock), turns, clock).play(gameId, p1, 0);

        assertEquals(0, state.getConsecutiveTimeouts(p1));
        assertEquals(1, state.getConsecutiveTimeouts(p2));
    }

    private User player(String name) {
        var user = new User(name, "hash", name + "@example.test");
        user.setId(UUID.randomUUID());
        return user;
    }
}
