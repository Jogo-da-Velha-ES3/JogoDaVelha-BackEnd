package com.jogodavelha.game.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GameStateManagerTest {
    private final UUID gameId = UUID.randomUUID();
    private final UUID player1 = UUID.randomUUID();
    private final UUID player2 = UUID.randomUUID();
    private final String key = "game:state:" + gameId;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = mock(SecureRandom.class);
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private GameStateManager manager;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        manager = new GameStateManager(redis, mapper, 1800000, 5, random);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsCompleteStateAtomicallyWithOneDraw(boolean player1Starts) throws Exception {
        when(random.nextBoolean()).thenReturn(player1Starts);
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        manager.create(gameId, player1, player2, 10000);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(key), json.capture(), eq(1800000L), eq(TimeUnit.MILLISECONDS));
        GameState stored = mapper.readValue(json.getValue(), GameState.class);
        assertEquals(player1, stored.getPlayer1Id());
        assertEquals(player2, stored.getPlayer2Id());
        assertEquals(player1Starts ? player1 : player2, stored.getFirstPlayerId());
        assertEquals(stored.getFirstPlayerId(), stored.getCurrentTurnPlayerId());
        assertEquals(0, stored.getVersion());
        verify(random, times(1)).nextBoolean();
    }

    @Test
    void legacyCreationStillUsesTheGivenFirstPlayer() throws Exception {
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        manager.create(gameId, player1, 10000);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(key), json.capture(), eq(1800000L), eq(TimeUnit.MILLISECONDS));
        GameState stored = mapper.readValue(json.getValue(), GameState.class);
        assertEquals(player1, stored.getFirstPlayerId());
        assertEquals(GameState.EMPTY_BOARD, stored.getBoard());
        verifyNoInteractions(random);
    }

    @Test
    void existingStateIsNotOverwritten() {
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(false);
        assertThrows(IllegalStateException.class, () -> manager.create(gameId, player1, player2, 10000));
        verify(values).setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS));
        verify(values, never()).set(anyString(), any());
    }

    @Test
    void recoveryListsGameIdsWithScanAndClosesTheCursor() {
        var cursor = mock(Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(key);

        assertEquals(List.of(gameId), manager.findAllGameIds());

        verify(cursor).close();
        verify(redis, never()).keys(anyString());
    }

    @Test
    void returnsOnlyTheResultOfTheSuccessfulCasAttempt() throws Exception {
        var state = GameState.builder().gameId(gameId).version(4).build();
        String first = mapper.writeValueAsString(state);
        state.setVersion(5);
        when(values.get(key)).thenReturn(first, mapper.writeValueAsString(state));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(false, true);

        long versionSeen = manager.updateWithResult(gameId,
                current -> new GameStateManager.Update<>(current, current.getVersion()));

        assertEquals(5, versionSeen);
    }

    @Test
    void retriesRoundStartOnLatestStateWithoutNewDrawOrDeadline() throws Exception {
        GameState state = GameState.builder()
                .gameId(gameId).player1Id(player1).player2Id(player2)
                .firstPlayerId(player2).currentTurnPlayerId(player2)
                .board("X---------------").roundNumber(1).version(4)
                .player1ConsecutiveTimeouts(1).player2ConsecutiveTimeouts(2).build();
        String beforeConflict = mapper.writeValueAsString(state);
        state.recordTimeout(player1);
        state.setVersion(5);
        String afterConflict = mapper.writeValueAsString(state);
        when(values.get(key)).thenReturn(beforeConflict, afterConflict);
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(false, true);
        manager.update(gameId, current -> {
            current.startRound(2, 10000, 100000);
            return current;
        });
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of(key)),
                anyString(), json.capture(), eq("1800"));
        for (String attempt : json.getAllValues()) {
            GameState updated = mapper.readValue(attempt, GameState.class);
            assertEquals(110000, updated.getTurnDeadlineEpochMillis());
            assertEquals(player1, updated.getCurrentTurnPlayerId());
            assertEquals(GameState.EMPTY_BOARD, updated.getBoard());
        }
        GameState updated = mapper.readValue(json.getAllValues().getLast(), GameState.class);
        assertEquals(6, updated.getVersion());
        assertEquals(2, updated.getConsecutiveTimeouts(player1));
        assertEquals(2, updated.getConsecutiveTimeouts(player2));
        verifyNoInteractions(random);
    }

    @Test
    void cannotUpdateMissingState() {
        assertThrows(GameStateNotFoundException.class, () -> manager.update(gameId, state -> state));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
        verifyNoInteractions(random);
    }

    @Test
    void updateReturnsCommittedStateAndServesSequentialUpdatesFromCache() {
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(true);
        manager.create(gameId, player1, player2, 10000);

        GameState first = manager.update(gameId, state -> {
            state.setBoard("X---------------");
            return state;
        });
        GameState second = manager.update(gameId, state -> {
            state.setBoard("XO--------------");
            return state;
        });

        assertEquals(1, first.getVersion());
        assertEquals(2, second.getVersion());
        assertEquals("XO--------------", second.getBoard());
        // O estado commitado fica em cache: nenhuma releitura precede os CAS.
        verify(values, never()).get(anyString());
        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of(key)),
                anyString(), anyString(), eq("1800"));
    }

    @Test
    void mutatorRejectionOnCachedStateFallsBackToFreshRead() throws Exception {
        ArgumentCaptor<String> created = ArgumentCaptor.forClass(String.class);
        when(values.setIfAbsent(eq(key), created.capture(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        manager.create(gameId, player1, 10000);
        when(values.get(key)).thenReturn(created.getValue());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.update(gameId, state -> {
                    throw new IllegalStateException("turno inválido");
                }));

        assertEquals("turno inválido", failure.getMessage());
        verify(values, times(1)).get(key);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void failedCasDoesNotEvictANewerCommittedState() throws Exception {
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        AtomicInteger casCalls = new AtomicInteger();
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (casCalls.getAndIncrement() == 0) {
                        // Outra operação do mesmo nó commita a versão 1 enquanto esta
                        // tentativa ainda perde o CAS.
                        manager.update(gameId, state -> {
                            state.setBoard("XO--------------");
                            return state;
                        });
                        return false;
                    }
                    return true;
                });
        manager.create(gameId, player1, player2, 10000);

        GameState committed = manager.update(gameId, state -> {
            state.setBoard("X---------------");
            return state;
        });

        assertEquals(2, committed.getVersion());
        assertEquals("X---------------", committed.getBoard());
        // O commit mais novo sobreviveu à falha do CAS antigo: nenhuma releitura.
        verify(values, never()).get(anyString());
        verify(redis, times(3)).execute(any(RedisScript.class), eq(List.of(key)),
                anyString(), anyString(), eq("1800"));
    }

    @Test
    void expiredCacheEntryFallsBackToTheRedisRead() throws Exception {
        GameStateManager expiring = new GameStateManager(redis, mapper, 0, 5, random);
        ArgumentCaptor<String> created = ArgumentCaptor.forClass(String.class);
        when(values.setIfAbsent(eq(key), created.capture(), eq(0L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        expiring.create(gameId, player1, 10000);
        when(values.get(key)).thenReturn(created.getValue());
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), eq("0")))
                .thenReturn(true);

        GameState updated = expiring.update(gameId, state -> state);

        assertEquals(1, updated.getVersion());
        verify(values, times(1)).get(key);
    }

    @Test
    void missingRedisKeyDiscardsTheLocalEntryAndReportsNotFound() {
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(true);
        manager.create(gameId, player1, player2, 10000);
        manager.update(gameId, state -> state);

        // A chave sumiu do Redis (TTL ou delete) enquanto o cache local ainda a tinha.
        when(values.get(key)).thenReturn(null);
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(false);

        assertThrows(GameStateNotFoundException.class, () -> manager.update(gameId, state -> state));
        verify(values, atLeastOnce()).get(key);
    }

    @Test
    void recoveryScanPrunesCacheEntriesMissingFromRedis() throws Exception {
        ArgumentCaptor<String> created = ArgumentCaptor.forClass(String.class);
        when(values.setIfAbsent(eq(key), created.capture(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        manager.create(gameId, player1, player2, 10000);

        UUID other = UUID.randomUUID();
        var cursor = mock(Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("game:state:" + other);
        assertEquals(List.of(other), manager.findAllGameIds());

        when(values.get(key)).thenReturn(created.getValue());
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(true);

        manager.update(gameId, state -> state);
        // Sem a poda, o update usaria o cache e nunca leria o Redis.
        verify(values, times(1)).get(key);
    }
}
