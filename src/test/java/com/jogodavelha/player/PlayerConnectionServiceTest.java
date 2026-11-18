package com.jogodavelha.player;

import com.jogodavelha.game.PlayerConnectionService;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerConnectionServiceTest {
    private static final long GRACE_MILLIS = 1_500;

    private final PlayerService players = mock(PlayerService.class);
    private final PlayerSessionRegistry sessions = new PlayerSessionRegistry();
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
    private final List<Instant> scheduledTimes = new ArrayList<>();
    private final PlayerConnectionService connections =
            new PlayerConnectionService(players, sessions, scheduler, GRACE_MILLIS);

    @BeforeEach
    void setUp() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            scheduledTimes.add(invocation.getArgument(1));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            scheduledFutures.add(future);
            return future;
        });
    }

    @Test
    void disconnectsP1OnlyAfterItsLastSessionEndsAndOnlyOnce() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", p1, "P1", p2);
        when(players.markDisconnected("0042", p1)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        assertTrue(connections.connect("p1-session-a", entry).firstPlayerSession());
        assertFalse(connections.connect("p1-session-b", entry).firstPlayerSession());
        connections.disconnect("p1-session-a", confirmed::set);
        assertTrue(scheduledTasks.isEmpty());
        connections.disconnect("p1-session-b", confirmed::set);
        connections.disconnect("p1-session-b", confirmed::set);

        assertEquals(1, scheduledTasks.size());
        assertNull(confirmed.get());
        verify(players, never()).markDisconnected("0042", p1);

        scheduledTasks.get(0).run();

        assertEquals(entry, confirmed.get());
        verify(players, times(1)).markDisconnected("0042", p1);
        verify(players, never()).markDisconnected("0042", p2);
    }

    @Test
    void disconnectForP2UsesItsSlotAndP1AsOpponent() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", p2, "P2", p1);
        when(players.markDisconnected("0042", p2)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        connections.connect("p2-session", entry);
        connections.disconnect("p2-session", confirmed::set);
        scheduledTasks.get(0).run();

        assertEquals(entry, confirmed.get());
        verify(players).markDisconnected("0042", p2);
    }

    @Test
    void newSessionDuringGraceWindowCancelsPendingDisconnect() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markDisconnected("0042", playerId)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        connections.connect("session-old", entry);
        connections.disconnect("session-old", confirmed::set);
        assertEquals(1, scheduledTasks.size());

        connections.connect("session-new", entry);

        verify(scheduledFutures.get(0)).cancel(false);
        assertNull(confirmed.get());

        // Mesmo que a tarefa cancelada fosse executada, ela não confirma a desconexão.
        scheduledTasks.get(0).run();
        assertNull(confirmed.get());
        verify(players, never()).markDisconnected("0042", playerId);
    }

    @Test
    void sessionRegisteredBeforeOldSessionEndsNeverSchedulesDisconnect() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());

        connections.connect("session-new", entry);
        connections.connect("session-old", entry);
        connections.disconnect("session-old", ignored -> fail("Não deve confirmar desconexão."));

        assertTrue(scheduledTasks.isEmpty());
        verify(players, never()).markDisconnected(anyString(), any(UUID.class));
        assertEquals(1, sessions.activeEntries().size());
    }

    @Test
    void pendingDisconnectDoesNotFireWhileAnotherSessionExists() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markDisconnected("0042", playerId)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        connections.connect("session-a", entry);
        connections.connect("session-b", entry);
        connections.disconnect("session-a", confirmed::set);
        connections.disconnect("session-b", confirmed::set);
        connections.connect("session-c", entry);

        scheduledTasks.get(0).run();

        assertNull(confirmed.get());
        verify(players, never()).markDisconnected("0042", playerId);
    }

    @Test
    void definitiveDisconnectAfterGraceWindowRunsOnce() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markDisconnected("0042", playerId)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        connections.connect("session-a", entry);
        connections.disconnect("session-a", confirmed::set);
        scheduledTasks.get(0).run();
        scheduledTasks.get(0).run();

        assertEquals(entry, confirmed.get());
        verify(players, times(1)).markDisconnected("0042", playerId);
    }

    @Test
    void stalePendingDisconnectTaskNeverConfirmsDisconnect() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markDisconnected("0042", playerId)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        connections.connect("session-a", entry);
        connections.disconnect("session-a", confirmed::set);
        connections.connect("session-b", entry);
        connections.disconnect("session-b", confirmed::set);

        assertEquals(2, scheduledTasks.size());
        verify(scheduledFutures.get(0)).cancel(false);

        scheduledTasks.get(0).run();
        assertNull(confirmed.get());
        verify(players, never()).markDisconnected("0042", playerId);

        scheduledTasks.get(1).run();
        assertEquals(entry, confirmed.get());
        verify(players, times(1)).markDisconnected("0042", playerId);
    }

    @Test
    void reconnectAfterConfirmedDisconnectIsReportedAsReconnect() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markConnected("0042", playerId)).thenReturn(true);
        when(players.markDisconnected("0042", playerId)).thenReturn(true);
        AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

        connections.connect("session-a", entry);
        connections.disconnect("session-a", confirmed::set);
        scheduledTasks.get(0).run();
        assertEquals(entry, confirmed.get());

        assertTrue(connections.connect("session-b", entry).reconnected());
        assertEquals(1, sessions.activeEntries().size());
        verify(players, times(1)).markDisconnected("0042", playerId);
    }

    @Test
    void graceWindowIsScheduledWithConfiguredDelay() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());

        connections.connect("session-a", entry);
        Instant before = Instant.now();
        connections.disconnect("session-a", ignored -> {});
        Instant after = Instant.now();

        assertEquals(1, scheduledTasks.size());
        Instant earliest = before.plusMillis(GRACE_MILLIS);
        Instant latest = after.plusMillis(GRACE_MILLIS);
        assertFalse(scheduledTimes.get(0).isBefore(earliest),
                "A desconexão não pode ser confirmada antes do fim da janela de tolerância.");
        assertFalse(scheduledTimes.get(0).isAfter(latest),
                "A desconexão deve ser agendada para o fim da janela de tolerância.");
    }

    @Test
    void reconnectTransitionIsReportedOnlyForFirstSessionAfterDisconnect() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        when(players.markConnected("0042", playerId)).thenReturn(true, false);

        assertTrue(connections.connect("session-a", entry).reconnected());
        assertFalse(connections.connect("session-b", entry).reconnected());
    }

    @Test
    void refreshesPresenceOncePerActivePlayerAndRoom() {
        UUID playerId = UUID.randomUUID();
        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
        connections.connect("session-a", entry);
        connections.connect("session-b", entry);

        connections.refreshActivePresence();

        verify(players, times(1)).refreshConnected("0042", playerId);
    }

    @Test
    void concurrentPlayersConfirmDisconnectsExactlyOnce() throws Exception {
        ThreadPoolTaskScheduler realScheduler = newScheduler(4);
        try {
            PlayerConnectionService service =
                    new PlayerConnectionService(players, sessions, realScheduler, 300);
            when(players.markDisconnected(anyString(), any(UUID.class))).thenReturn(true);
            int iterations = 30;
            CountDownLatch confirmed = new CountDownLatch(iterations);
            var executor = Executors.newFixedThreadPool(8);
            try {
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < iterations; i++) {
                    int index = i;
                    futures.add(executor.submit(() -> {
                        UUID playerId = UUID.randomUUID();
                        var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
                        service.connect("session-" + index + "-old", entry);
                        service.disconnect("session-" + index + "-old", ignored -> confirmed.countDown());
                        service.connect("session-" + index + "-new", entry);
                        service.disconnect("session-" + index + "-new", ignored -> confirmed.countDown());
                    }));
                }
                for (var future : futures) {
                    future.get(15, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            assertTrue(confirmed.await(10, TimeUnit.SECONDS),
                    "Cada jogador sem sessões deve ter sua desconexão confirmada uma única vez.");
            verify(players, times(iterations)).markDisconnected(anyString(), any(UUID.class));
            assertTrue(sessions.activeEntries().isEmpty());
        } finally {
            realScheduler.shutdown();
        }
    }

    @Test
    void reconnectDuringGraceWindowWithRealSchedulerPreventsDisconnect() throws Exception {
        ThreadPoolTaskScheduler realScheduler = newScheduler(2);
        try {
            PlayerConnectionService service =
                    new PlayerConnectionService(players, sessions, realScheduler, 500);
            UUID playerId = UUID.randomUUID();
            var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
            AtomicReference<Boolean> confirmed = new AtomicReference<>(false);

            service.connect("session-old", entry);
            service.disconnect("session-old", ignored -> confirmed.set(true));
            service.connect("session-new", entry);

            await().pollDelay(700, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> assertEquals(1, sessions.activeEntries().size()));

            assertFalse(confirmed.get());
            verify(players, never()).markDisconnected("0042", playerId);
        } finally {
            realScheduler.shutdown();
        }
    }

    @Test
    void slowDisconnectCallbackDoesNotBlockOtherPlayers() throws Exception {
        ThreadPoolTaskScheduler realScheduler = newScheduler(2);
        try {
            PlayerConnectionService service =
                    new PlayerConnectionService(players, sessions, realScheduler, 100);
            when(players.markDisconnected(anyString(), any(UUID.class))).thenReturn(true);

            UUID slowPlayer = UUID.randomUUID();
            UUID fastPlayer = UUID.randomUUID();
            var slowEntry = new PlayerSessionRegistry.Entry("0042", slowPlayer, "P1", fastPlayer);
            var fastEntry = new PlayerSessionRegistry.Entry("0042", fastPlayer, "P2", slowPlayer);

            CountDownLatch callbackStarted = new CountDownLatch(1);
            CountDownLatch releaseCallback = new CountDownLatch(1);
            CountDownLatch fastConfirmed = new CountDownLatch(1);

            service.connect("slow-session", slowEntry);
            service.disconnect("slow-session", ignored -> {
                callbackStarted.countDown();
                try {
                    releaseCallback.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(callbackStarted.await(10, TimeUnit.SECONDS),
                    "A confirmação da desconexão lenta deveria ter iniciado.");

            service.connect("fast-session", fastEntry);
            service.disconnect("fast-session", ignored -> fastConfirmed.countDown());
            assertTrue(fastConfirmed.await(10, TimeUnit.SECONDS),
                    "A desconexão de outro jogador não pode ficar bloqueada pelo callback lento.");

            releaseCallback.countDown();
        } finally {
            realScheduler.shutdown();
        }
    }

    @Test
    void concurrentConnectAndDisconnectForSamePlayerStayConsistent() throws Exception {
        ThreadPoolTaskScheduler realScheduler = newScheduler(8);
        try {
            PlayerConnectionService service =
                    new PlayerConnectionService(players, sessions, realScheduler, 300);
            when(players.markDisconnected(anyString(), any(UUID.class))).thenReturn(true);
            int iterations = 30;
            var playerIds = new java.util.concurrent.CopyOnWriteArrayList<UUID>();
            var executor = Executors.newFixedThreadPool(16);
            try {
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < iterations; i++) {
                    UUID playerId = UUID.randomUUID();
                    playerIds.add(playerId);
                    var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
                    String sessionId = "session-" + i;
                    CountDownLatch start = new CountDownLatch(2);
                    futures.add(executor.submit(() -> {
                        try {
                            start.await();
                            service.connect(sessionId, entry);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                    futures.add(executor.submit(() -> {
                        try {
                            start.await();
                            service.disconnect(sessionId, ignored -> {});
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                    start.countDown();
                    start.countDown();
                }
                for (var future : futures) {
                    future.get(20, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                int withoutSessions = 0;
                for (UUID playerId : playerIds) {
                    boolean hasSession = sessions.activeEntries().stream()
                            .anyMatch(active -> active.playerId().equals(playerId));
                    if (!hasSession) {
                        withoutSessions++;
                    }
                }
                verify(players, times(withoutSessions)).markDisconnected(anyString(), any(UUID.class));
                for (UUID playerId : playerIds) {
                    verify(players, atMost(1)).markDisconnected(anyString(), eq(playerId));
                }
            });
        } finally {
            realScheduler.shutdown();
        }
    }

    private static ThreadPoolTaskScheduler newScheduler(int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("test-disconnect-");
        scheduler.initialize();
        return scheduler;
    }
}
