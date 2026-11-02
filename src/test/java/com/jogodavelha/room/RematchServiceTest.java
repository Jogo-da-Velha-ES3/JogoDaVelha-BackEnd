package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.RoundResult;
import com.jogodavelha.game.draft.DraftService;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.websocket.RoomSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RematchServiceTest {
    @Mock private RoomRepository rooms;
    @Mock private GameRepository games;
    @Mock private RoomService roomService;
    @Mock private RematchStateManager states;
    @Mock private DraftService drafts;
    @Mock private DraftStateManager draftStates;
    @Mock private GameStateManager gameStates;
    @Mock private RoomSnapshotService snapshots;
    @Mock private TaskScheduler scheduler;

    private User player1;
    private User player2;
    private Room room;
    private Game previous;
    private Clock clock;
    private RematchService service;

    @BeforeEach
    void setUp() throws Exception {
        long now = System.currentTimeMillis();
        clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC);
        service = new RematchService(rooms, games, roomService, states, drafts, draftStates,
                gameStates, snapshots, scheduler, clock);
        player1 = user("p1");
        player2 = user("p2");
        room = new Room("0042", player1);
        room.join(player2);
        setId(room, UUID.randomUUID());
        previous = new Game(room, player1);
        previous.setPlayer2(player2);
        setId(previous, UUID.randomUUID());
        previous.registerRoundResult(RoundResult.PLAYER1_WIN);
        previous.registerRoundResult(RoundResult.PLAYER1_WIN);
        lenient().when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, player1.getId()))
                .thenReturn(Optional.of(room));
        lenient().when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, player2.getId()))
                .thenReturn(Optional.of(room));
        lenient().when(games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        lenient().when(games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(room.getId(), GameStatus.FINISHED))
                .thenReturn(Optional.of(previous));
        lenient().when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));
    }

    @Test
    void requestCreatesSingleFifteenSecondNegotiationAndPublishesSnapshot() {
        when(states.find(room.getId())).thenReturn(Optional.empty());
        when(states.begin(any(RematchState.class))).thenReturn(true);

        service.request("0042", player1.getId());

        var captor = org.mockito.ArgumentCaptor.forClass(RematchState.class);
        verify(states).begin(captor.capture());
        assertEquals(player1.getId(), captor.getValue().requesterId());
        assertEquals(previous.getId(), captor.getValue().gameId());
        assertEquals(clock.millis() + RematchService.RESPONSE_WINDOW_MILLIS,
                captor.getValue().deadlineEpochMillis());
        verify(snapshots).publish("0042");
    }

    @Test
    void rejectsDuplicateRequestAndExternalPlayer() {
        RematchState pending = pending();
        when(states.find(room.getId())).thenReturn(Optional.of(pending));
        assertThrows(IllegalStateException.class, () -> service.request("0042", player2.getId()));
        verify(states, never()).begin(any());

        UUID outsider = UUID.randomUUID();
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, outsider)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.request("0042", outsider));
    }

    @Test
    void doesNotAllowAnotherRequestForTheSameFinishedMatchAfterNegotiationEnds() {
        RematchState declined = pending().withStatus(RematchState.Status.DECLINED);
        when(states.find(room.getId())).thenReturn(Optional.of(declined));

        assertThrows(IllegalStateException.class, () -> service.request("0042", player2.getId()));

        verify(states, never()).begin(any());
    }

    @Test
    void doesNotAllowRematchRequestWhileAnotherDraftIsActive() {
        var eligible = new com.jogodavelha.game.draft.DraftEligibilityProvider.EligibleItems(
                java.util.List.of(UUID.randomUUID(), UUID.randomUUID()), java.util.List.of("A", "B"));
        var activeDraft = new com.jogodavelha.game.draft.DraftState(room.getId(), "0042",
                clock.millis() + com.jogodavelha.game.draft.DraftState.DURATION_MILLIS, 0,
                com.jogodavelha.game.draft.DraftState.Player.initial(player1.getId(), eligible),
                com.jogodavelha.game.draft.DraftState.Player.initial(player2.getId(), eligible));
        when(draftStates.find(room.getId())).thenReturn(Optional.of(activeDraft));

        assertThrows(IllegalStateException.class, () -> service.request("0042", player1.getId()));

        verify(states, never()).begin(any());
    }

    @Test
    void acceptanceCreatesFreshDraftOnceAndRetainsFinishedGame() {
        RematchState pending = pending();
        RematchState accepted = pending.withStatus(RematchState.Status.ACCEPTED);
        when(states.find(room.getId())).thenReturn(Optional.of(pending));
        when(states.transition(pending, RematchState.Status.ACCEPTED)).thenReturn(Optional.of(accepted));
        when(games.findById(previous.getId())).thenReturn(Optional.of(previous));
        when(draftStates.find(room.getId())).thenReturn(Optional.empty());
        when(states.acquireInitialization(eq(room.getId()), anyString())).thenReturn(true);

        service.accept("0042", player2.getId());

        verify(drafts).cancel(room.getId());
        verify(draftStates).delete(room.getId());
        verify(gameStates).delete(previous.getId());
        verify(drafts).start(room.getId(), "0042", player1.getId(), player2.getId());
        verify(roomService, never()).closeAfterMatch(anyString(), any(), anyString());
        verify(states).releaseInitialization(eq(room.getId()), anyString());
        assertEquals(GameStatus.FINISHED, previous.getStatus());
    }

    @Test
    void expiredNegotiationClosesRoomOnceAndCannotBeAccepted() {
        RematchState pending = pending();
        Clock expiredClock = Clock.fixed(Instant.ofEpochMilli(pending.deadlineEpochMillis() + 1), ZoneOffset.UTC);
        service = new RematchService(rooms, games, roomService, states, drafts, draftStates,
                gameStates, snapshots, scheduler, expiredClock);
        when(states.find(room.getId())).thenReturn(Optional.of(pending));
        when(states.transition(pending, RematchState.Status.EXPIRED))
                .thenReturn(Optional.of(pending.withStatus(RematchState.Status.EXPIRED)));

        assertThrows(IllegalStateException.class, () -> service.accept("0042", player2.getId()));

        verify(roomService).closeAfterMatch("0042", null, RoomClosedEvent.REMATCH_EXPIRED);
        verify(states, never()).transition(pending, RematchState.Status.ACCEPTED);
    }

    @Test
    void declineAndReturnToLobbyUseExistingRoomClosureMechanism() {
        RematchState pending = pending();
        when(states.find(room.getId())).thenReturn(Optional.of(pending));
        when(states.transition(pending, RematchState.Status.DECLINED))
                .thenReturn(Optional.of(pending.withStatus(RematchState.Status.DECLINED)));

        service.decline("0042", player2.getId());

        verify(roomService).closeAfterMatch("0042", player2.getId(), RoomClosedEvent.REMATCH_DECLINED);
        verify(states, never()).transition(pending, RematchState.Status.CANCELLED);
    }

    @Test
    void pendingDisconnectCancelsNegotiationAndClosesRoom() {
        RematchState pending = pending();
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, player1.getId()))
                .thenReturn(Optional.of(room));
        when(states.find(room.getId())).thenReturn(Optional.of(pending));
        when(states.transition(pending, RematchState.Status.CANCELLED))
                .thenReturn(Optional.of(pending.withStatus(RematchState.Status.CANCELLED)));

        service.onPlayerDisconnected("0042", player1.getId());

        verify(roomService).closeAfterMatch("0042", player1.getId(), RoomClosedEvent.REMATCH_DISCONNECTED);
    }

    @Test
    void disconnectWithoutPendingNegotiationLeavesRoomAlone() {
        when(rooms.findActiveMembership("0042", RoomCodeAllocator.ACTIVE, player1.getId()))
                .thenReturn(Optional.of(room));
        when(states.find(room.getId())).thenReturn(Optional.empty());

        service.onPlayerDisconnected("0042", player1.getId());

        verifyNoInteractions(roomService);
    }

    @Test
    void returnToLobbyCancelsPendingNegotiationBeforeClosing() {
        RematchState pending = pending();
        when(states.find(room.getId())).thenReturn(Optional.of(pending));
        when(states.transition(pending, RematchState.Status.CANCELLED))
                .thenReturn(Optional.of(pending.withStatus(RematchState.Status.CANCELLED)));

        service.returnToLobby("0042", player1.getId());

        verify(roomService).closeAfterMatch("0042", player1.getId(), RoomClosedEvent.RETURN_TO_LOBBY);
    }

    @Test
    void returnToLobbyDoesNotCloseWhenAcceptanceWonTheRace() {
        RematchState pending = pending();
        when(states.find(room.getId())).thenReturn(Optional.of(pending),
                Optional.of(pending.withStatus(RematchState.Status.ACCEPTED)));
        when(states.transition(pending, RematchState.Status.CANCELLED)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.returnToLobby("0042", player1.getId()));

        verify(roomService, never()).closeAfterMatch(anyString(), any(), anyString());
    }

    @Test
    void staleExpirationCannotCloseRoomForANewerNegotiation() {
        RematchState old = pending();
        RematchState newer = new RematchState(room.getId(), "0042", UUID.randomUUID(), player2.getId(),
                old.deadlineEpochMillis() + 1_000, 0, RematchState.Status.PENDING);
        when(states.find(room.getId())).thenReturn(Optional.of(newer));

        service.expire(room.getId(), old.gameId(), old.deadlineEpochMillis());

        verify(states, never()).transition(any(), any());
        verify(roomService, never()).closeAfterMatch(anyString(), any(), anyString());
    }

    @Test
    void concurrentAcceptanceAndDeclineProduceOneTerminalTransition() throws Exception {
        RematchState pending = pending();
        AtomicReference<RematchState> current = new AtomicReference<>(pending);
        when(states.find(room.getId())).thenAnswer(ignored -> Optional.of(current.get()));
        when(states.transition(any(RematchState.class), any(RematchState.Status.class))).thenAnswer(invocation -> {
            synchronized (current) {
                RematchState before = current.get();
                RematchState.Status target = invocation.getArgument(1);
                if (before.status() != RematchState.Status.PENDING) return Optional.empty();
                RematchState updated = before.withStatus(target);
                current.set(updated);
                return Optional.of(updated);
            }
        });
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> accept = executor.submit(() -> compete(start,
                    () -> service.accept("0042", player2.getId())));
            Future<Boolean> decline = executor.submit(() -> compete(start,
                    () -> service.decline("0042", player2.getId())));
            start.countDown();

            assertNotEquals(accept.get(), decline.get());
            assertTrue(current.get().status() == RematchState.Status.ACCEPTED
                    || current.get().status() == RematchState.Status.DECLINED);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean compete(CountDownLatch start, Runnable operation) throws InterruptedException {
        start.await();
        try {
            operation.run();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private RematchState pending() {
        return new RematchState(room.getId(), "0042", previous.getId(), player1.getId(),
                clock.millis() + RematchService.RESPONSE_WINDOW_MILLIS, 0, RematchState.Status.PENDING);
    }

    private static User user(String username) {
        User user = new User(username, "hash", username + "@example.test");
        user.setId(UUID.randomUUID());
        return user;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
