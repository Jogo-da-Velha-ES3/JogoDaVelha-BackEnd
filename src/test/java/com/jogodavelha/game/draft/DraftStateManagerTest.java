package com.jogodavelha.game.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.ConcurrentStateModificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class DraftStateManagerTest {
    private final UUID room = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID a = new UUID(0, 1);
    private final UUID b = new UUID(0, 2);
    private final String key = "draft:state:" + room;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private DraftStateManager manager;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        manager = new DraftStateManager(redis, mapper, 1800000, 3);
    }

    @Test
    void createsOnceWithRetentionLongerThanTheDraftAndSerializesTheSnapshot() throws Exception {
        when(values.setIfAbsent(eq(key), anyString(), eq(1800000L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true, false);
        DraftState initial = initial();

        manager.create(initial);
        assertThrows(IllegalStateException.class, () -> manager.create(initial));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values, times(2)).setIfAbsent(eq(key), json.capture(), eq(1800000L), eq(TimeUnit.MILLISECONDS));
        assertEquals(initial, mapper.readValue(json.getValue(), DraftState.class));
    }

    @Test
    void retryPreservesTheOtherPlayersConcurrentSelectionAndOriginalDeadline() throws Exception {
        DraftState first = initial();
        DraftState newer = first.withPlayer(first.player2().withCharacter(b)).withVersion(1);
        when(values.get(key)).thenReturn(mapper.writeValueAsString(first), mapper.writeValueAsString(newer));
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(0L, 1L);

        var change = manager.update(room, state -> state.withPlayer(state.player1().withCharacter(a))).orElseThrow();

        assertEquals(newer, change.before());
        assertEquals(a, change.after().player1().characterId());
        assertEquals(b, change.after().player2().characterId());
        assertEquals(2, change.after().version());
        assertEquals(21000, change.after().deadlineEpochMillis());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of(key)), anyString(), json.capture(), eq("1800000"));
        assertEquals(change.after(), mapper.readValue(json.getValue(), DraftState.class));
    }

    @Test
    void noOpDoesNotWriteOrIncrementVersion() throws Exception {
        when(values.get(key)).thenReturn(mapper.writeValueAsString(initial()));
        assertTrue(manager.update(room, state -> state).isEmpty());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void cleanupTargetsOnlyTheDraftOfTheGivenRoom() {
        manager.delete(room);
        verify(redis).delete(key);
        verifyNoMoreInteractions(redis);
    }

    @Test
    void refusesUpdatesOfMissingOrCorruptedState() {
        assertTrue(manager.find(room).isEmpty());
        assertThrows(NoSuchElementException.class, () -> manager.update(room, state -> state));
        when(values.get(key)).thenReturn("not-json");
        assertThrows(IllegalStateException.class, () -> manager.find(room));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void cannotExtendTheDeadlineThroughAnUpdate() throws Exception {
        when(values.get(key)).thenReturn(mapper.writeValueAsString(initial()));
        assertThrows(IllegalArgumentException.class, () -> manager.update(room, state ->
                new DraftState(room, "0042", 31000, state.version(), state.player1(), state.player2())));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void retriesAreBounded() throws Exception {
        when(values.get(key)).thenReturn(mapper.writeValueAsString(initial()));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);
        assertThrows(ConcurrentStateModificationException.class,
                () -> manager.update(room, state -> state.withPlayer(state.player1().withCharacter(a))));
        verify(redis, times(3)).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 19999, 20000})
    void retentionCannotRemoveTheStateBeforeAutoChoice(long retention) {
        assertThrows(IllegalArgumentException.class, () -> new DraftStateManager(redis, mapper, retention, 3));
    }

    private DraftState initial() {
        var items = new DraftEligibilityProvider.EligibleItems(List.of(a, b), List.of("BOMBA", "ESCUDO", "AMPULHETA"));
        return new DraftState(room, "0042", 21000, 0,
                DraftState.Player.initial(p1, items), DraftState.Player.initial(p2, items));
    }
}
