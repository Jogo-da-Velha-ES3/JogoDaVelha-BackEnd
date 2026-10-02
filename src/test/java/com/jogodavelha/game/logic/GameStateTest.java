package com.jogodavelha.game.logic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    private ObjectMapper objectMapper;
    private UUID gameId;
    private UUID player1Id;
    private UUID player2Id;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        gameId = UUID.randomUUID();
        player1Id = UUID.randomUUID();
        player2Id = UUID.randomUUID();
    }

    @Test
    void testCreateInitial() {
        GameState state = GameState.createInitial(gameId, player1Id, 10000);

        assertEquals(gameId, state.getGameId());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(player1Id, state.getFirstPlayerId());
        assertEquals(player1Id, state.getCurrentTurnPlayerId());
        assertEquals(10000, state.getTurnDurationMillis());
        assertEquals(1, state.getRoundNumber());
        assertFalse(state.isSuddenDeath());
        assertEquals(0L, state.getVersion());
    }

    @Test
    void testGetRemainingTimeMillis() {
        long now = Instant.now().toEpochMilli();
        GameState state = GameState.builder()
                .gameId(gameId)
                .board(GameState.EMPTY_BOARD)
                .firstPlayerId(player1Id)
                .currentTurnPlayerId(player1Id)
                .turnDurationMillis(10000)
                .turnDeadlineEpochMillis(now + 5000)
                .roundNumber(1)
                .suddenDeath(false)
                .version(0L)
                .build();

        long remaining = state.getRemainingTimeMillis();
        assertTrue(remaining > 0);
        assertTrue(remaining <= 5000);
    }

    @Test
    void testGetRemainingTimeMillisExpired() {
        long now = Instant.now().toEpochMilli();
        GameState state = GameState.builder()
                .gameId(gameId)
                .board(GameState.EMPTY_BOARD)
                .firstPlayerId(player1Id)
                .currentTurnPlayerId(player1Id)
                .turnDurationMillis(10000)
                .turnDeadlineEpochMillis(now - 1000)
                .roundNumber(1)
                .suddenDeath(false)
                .version(0L)
                .build();

        assertEquals(0, state.getRemainingTimeMillis());
    }

    @Test
    void testSerializationAndDeserialization() throws JsonProcessingException {
        GameState original = GameState.createInitial(gameId, player1Id, 10000);

        String json = objectMapper.writeValueAsString(original);
        GameState deserialized = objectMapper.readValue(json, GameState.class);

        assertEquals(original.getGameId(), deserialized.getGameId());
        assertEquals(original.getBoard(), deserialized.getBoard());
        assertEquals(original.getFirstPlayerId(), deserialized.getFirstPlayerId());
        assertEquals(original.getCurrentTurnPlayerId(), deserialized.getCurrentTurnPlayerId());
        assertEquals(original.getTurnDurationMillis(), deserialized.getTurnDurationMillis());
        assertEquals(original.getTurnDeadlineEpochMillis(), deserialized.getTurnDeadlineEpochMillis());
        assertEquals(original.getRoundNumber(), deserialized.getRoundNumber());
        assertEquals(original.isSuddenDeath(), deserialized.isSuddenDeath());
        assertEquals(original.getVersion(), deserialized.getVersion());
    }

    @Test
    void testDeserializationToleratesNewField() throws JsonProcessingException {
        String jsonWithNewField = String.format("""
            {
                "gameId": "%s",
                "board": "----------------",
                "firstPlayerId": "%s",
                "currentTurnPlayerId": "%s",
                "turnDurationMillis": 10000,
                "turnDeadlineEpochMillis": %d,
                "roundNumber": 1,
                "suddenDeath": false,
                "version": 0,
                "newFutureField": "some value"
            }
            """, gameId, player1Id, player1Id, Instant.now().toEpochMilli() + 10000);

        GameState state = objectMapper.readValue(jsonWithNewField, GameState.class);

        assertEquals(gameId, state.getGameId());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(player1Id, state.getFirstPlayerId());
    }

    @Test
    void testDeserializationToleratesMissingField() throws JsonProcessingException {
        String jsonWithMissingField = String.format("""
            {
                "gameId": "%s",
                "board": "----------------",
                "firstPlayerId": "%s",
                "currentTurnPlayerId": "%s",
                "turnDurationMillis": 10000,
                "turnDeadlineEpochMillis": %d,
                "roundNumber": 1,
                "suddenDeath": false,
                "version": 0
            }
            """, gameId, player1Id, player1Id, Instant.now().toEpochMilli() + 10000);

        GameState state = objectMapper.readValue(jsonWithMissingField, GameState.class);

        assertEquals(gameId, state.getGameId());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(player1Id, state.getFirstPlayerId());
    }

    @Test
    void testConstants() {
        assertEquals(16, GameState.BOARD_SIZE);
        assertEquals("----------------", GameState.EMPTY_BOARD);
        assertEquals(10000, GameState.DEFAULT_TURN_DURATION_MILLIS);
    }

    @Test
    void testBuilderPattern() {
        GameState state = GameState.builder()
                .gameId(gameId)
                .board("X---------------")
                .firstPlayerId(player1Id)
                .currentTurnPlayerId(player2Id)
                .turnDurationMillis(5000)
                .turnDeadlineEpochMillis(Instant.now().toEpochMilli() + 5000)
                .roundNumber(2)
                .suddenDeath(true)
                .version(5L)
                .build();

        assertEquals(gameId, state.getGameId());
        assertEquals("X---------------", state.getBoard());
        assertEquals(player1Id, state.getFirstPlayerId());
        assertEquals(player2Id, state.getCurrentTurnPlayerId());
        assertEquals(5000, state.getTurnDurationMillis());
        assertEquals(2, state.getRoundNumber());
        assertTrue(state.isSuddenDeath());
        assertEquals(5L, state.getVersion());
    }
}
