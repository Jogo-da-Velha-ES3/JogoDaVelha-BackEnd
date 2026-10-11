package com.jogodavelha.game.logic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsBothPlayersWithFixedSymbolsAndRandomStarter(boolean player1Starts) {
        GameState state = completeState(player1Starts);
        assertEquals(player1Id, state.getPlayer1Id());
        assertEquals(player2Id, state.getPlayer2Id());
        assertEquals(player1Starts ? player1Id : player2Id, state.getFirstPlayerId());
        assertEquals(state.getFirstPlayerId(), state.getCurrentTurnPlayerId());
        assertEquals('X', state.getPlayerSymbol(player1Id));
        assertEquals('O', state.getPlayerSymbol(player2Id));
        assertEquals(player2Id, state.getOpponentId(player1Id));
        assertEquals(player1Id, state.getOpponentId(player2Id));
        assertEquals(0, state.getConsecutiveTimeouts(player1Id));
        assertEquals(0, state.getConsecutiveTimeouts(player2Id));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void roundsAlternateWithoutNewDrawOrResettingTimeouts(boolean player1Starts) {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextBoolean()).thenReturn(player1Starts);
        GameState state = GameState.createInitial(gameId, player1Id, player2Id, 10000, random);
        UUID firstPlayer = state.getFirstPlayerId();
        state.recordTimeout(player1Id);
        state.recordTimeout(player1Id);
        state.recordTimeout(player2Id);
        state.setVersion(7);
        for (int round = 1; round <= 3; round++) {
            state.setBoard("XXOO------------");
            long duration = round == 3 ? 5000 : 10000;
            state.startRound(round, duration, 100000);
            assertEquals(GameState.EMPTY_BOARD, state.getBoard());
            assertEquals(round, state.getRoundNumber());
            assertEquals(round == 3, state.isSuddenDeath());
            assertEquals(firstPlayer, state.getFirstPlayerId());
            assertEquals(round == 2 ? state.getOpponentId(firstPlayer) : firstPlayer,
                    state.getCurrentTurnPlayerId());
            assertEquals(duration, state.getTurnDurationMillis());
            assertEquals(100000 + duration, state.getTurnDeadlineEpochMillis());
            assertEquals(2, state.getConsecutiveTimeouts(player1Id));
            assertEquals(1, state.getConsecutiveTimeouts(player2Id));
            assertEquals('X', state.getPlayerSymbol(player1Id));
            assertEquals('O', state.getPlayerSymbol(player2Id));
            assertEquals(7, state.getVersion());
        }
        verify(random, times(1)).nextBoolean();
    }

    @Test
    void resetsOnlyTheSelectedPlayersTimeouts() {
        GameState state = completeState(true);
        state.recordTimeout(player1Id);
        state.recordTimeout(player2Id);
        state.recordTimeout(player2Id);
        state.resetTimeouts(player1Id);
        assertEquals(0, state.getConsecutiveTimeouts(player1Id));
        assertEquals(2, state.getConsecutiveTimeouts(player2Id));
        state.resetTimeouts(player2Id);
        assertEquals(0, state.getConsecutiveTimeouts(player2Id));
    }

    @Test
    void serializesPlayersAndTimeouts() throws JsonProcessingException {
        GameState state = completeState(false);
        state.recordTimeout(player1Id);
        state.recordTimeout(player2Id);
        state.recordTimeout(player2Id);
        state.startRound(2, 10000, 100000);
        GameState restored = objectMapper.readValue(objectMapper.writeValueAsString(state), GameState.class);
        assertEquals(state.getPlayer1Id(), restored.getPlayer1Id());
        assertEquals(state.getPlayer2Id(), restored.getPlayer2Id());
        assertEquals(state.getFirstPlayerId(), restored.getFirstPlayerId());
        assertEquals(state.getCurrentTurnPlayerId(), restored.getCurrentTurnPlayerId());
        assertEquals(1, restored.getConsecutiveTimeouts(player1Id));
        assertEquals(2, restored.getConsecutiveTimeouts(player2Id));
        assertEquals('X', restored.getPlayerSymbol(player1Id));
        assertEquals('O', restored.getPlayerSymbol(player2Id));
    }

    @Test
    void rejectsInvalidParticipantsAndDurationBeforeDrawing() {
        SecureRandom random = mock(SecureRandom.class);
        assertThrows(IllegalArgumentException.class,
                () -> GameState.createInitial(gameId, player1Id, player1Id, 10000, random));
        assertThrows(IllegalArgumentException.class,
                () -> GameState.createInitial(gameId, null, player2Id, 10000, random));
        assertThrows(IllegalArgumentException.class,
                () -> GameState.createInitial(gameId, player1Id, null, 10000, random));
        assertThrows(IllegalArgumentException.class,
                () -> GameState.createInitial(null, player1Id, player2Id, 10000, random));
        assertThrows(IllegalArgumentException.class,
                () -> GameState.createInitial(gameId, player1Id, player2Id, 0, random));
        verifyNoInteractions(random);
    }

    @Test
    void rejectsUnknownPlayerWithoutChangingTimeouts() {
        GameState state = completeState(true);
        UUID outsider = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> state.getPlayerSymbol(outsider));
        assertThrows(IllegalArgumentException.class, () -> state.getOpponentId(outsider));
        assertThrows(IllegalArgumentException.class, () -> state.getConsecutiveTimeouts(outsider));
        assertThrows(IllegalArgumentException.class, () -> state.recordTimeout(outsider));
        assertThrows(IllegalArgumentException.class, () -> state.resetTimeouts(outsider));
        assertThrows(IllegalArgumentException.class, () -> state.getPlayerSymbol(null));
        assertEquals(0, state.getConsecutiveTimeouts(player1Id));
        assertEquals(0, state.getConsecutiveTimeouts(player2Id));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void rejectsInvalidRoundWithoutClearingBoard(int round) {
        GameState state = completeState(true);
        state.setBoard("X---------------");
        assertThrows(IllegalArgumentException.class, () -> state.startRound(round, 10000, 100000));
        assertEquals("X---------------", state.getBoard());
        assertEquals(1, state.getRoundNumber());
    }

    private GameState completeState(boolean player1Starts) {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextBoolean()).thenReturn(player1Starts);
        return GameState.createInitial(gameId, player1Id, player2Id, 10000, random);
    }
}
