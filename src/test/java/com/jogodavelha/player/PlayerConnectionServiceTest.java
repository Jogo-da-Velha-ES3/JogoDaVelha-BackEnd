package com.jogodavelha.player;

import com.jogodavelha.game.PlayerConnectionService;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    void concurrentDisconnectAndReconnectNeverProduceUndueOrDuplicateDisconnects() throws Exception {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler realScheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        realScheduler.setPoolSize(4);
        realScheduler.setThreadNamePrefix("test-disconnect-");
        realScheduler.initialize();
        try {
            PlayerConnectionService service =
                    new PlayerConnectionService(players, sessions, realScheduler, 300);
            UUID playerId = UUID.randomUUID();
            var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
            int iterations = 30;
            var executor = Executors.newFixedThreadPool(4);
            try {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < iterations; i++) {
                    int index = i;
                    futures.add(executor.submit(() -> {
                        service.connect("session-" + index + "-old", entry);
                        service.disconnect("session-" + index + "-old", ignored -> {});
                        service.connect("session-" + index + "-new", entry);
                        service.disconnect("session-" + index + "-new", ignored -> {});
                    }));
                }
                for (var future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }

            Thread.sleep(600);

            // Nenhuma sessão permanece ativa: cada jogador ficou sem sessões ao final,
            // portanto no máximo uma desconexão confirmada por iteração sem reconexão.
            verify(players, atMost(iterations * 2)).markDisconnected("0042", playerId);
        } finally {
            realScheduler.shutdown();
        }
    }

    @Test
    void reconnectDuringGraceWindowWithRealSchedulerPreventsDisconnect() throws Exception {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler realScheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        realScheduler.setPoolSize(2);
        realScheduler.setThreadNamePrefix("test-grace-");
        realScheduler.initialize();
        try {
            PlayerConnectionService service =
                    new PlayerConnectionService(players, sessions, realScheduler, 500);
            UUID playerId = UUID.randomUUID();
            var entry = new PlayerSessionRegistry.Entry("0042", playerId, "P1", UUID.randomUUID());
            AtomicReference<PlayerSessionRegistry.Entry> confirmed = new AtomicReference<>();

            service.connect("session-old", entry);
            service.disconnect("session-old", confirmed::set);
            service.connect("session-new", entry);

            Thread.sleep(700);

            assertNull(confirmed.get());
            verify(players, never()).markDisconnected("0042", playerId);
            assertEquals(1, sessions.activeEntries().size());
        } finally {
            realScheduler.shutdown();
        }
    }
}
