package com.jogodavelha.game.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
}
