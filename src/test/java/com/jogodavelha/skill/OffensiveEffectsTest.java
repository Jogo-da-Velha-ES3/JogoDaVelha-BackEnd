package com.jogodavelha.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OffensiveEffectsTest {
    private final UUID gameId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();
    private final GameValidator validator = new GameValidator();
    private final SkillEngine engine = new SkillEngine(List.of(
            new BombEffect(), new TornadoEffect(), new BlackHoleEffect(), new FreezeEffect()), validator);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bombRemovesOnlyTheOpponentsSelectedPiece(boolean player1Acts) {
        GameState state = state("X----O-O--------");
        UUID caster = player1Acts ? p1 : p2;
        int target = player1Acts ? 5 : 0;
        char[] expected = state.getBoard().toCharArray();
        expected[target] = '-';

        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "BOMBA", new SkillContext(caster, target)));

        assertEquals(new String(expected), state.getBoard());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 6, 9})
    void tornadoMovesToEachOrthogonalNeighbor(int destination) {
        GameState state = state("X----O----------");

        assertEquals(SkillResult.APPLIED,
                engine.useSkill(state, "TORNADO", new SkillContext(p1, 5, destination)));

        assertMovedTo(state, 5, destination);
    }

    @ParameterizedTest
    @CsvSource({"3, 4", "4, 3", "5, 10", "5, 15", "5, 5", "5, -1", "5, 16"})
    void tornadoRejectsRowWrappingDiagonalsAndInvalidDestinations(int source, int destination) {
        char[] board = GameState.EMPTY_BOARD.toCharArray();
        board[source] = 'O';
        GameState state = state(new String(board));
        GameState before = copy(state);

        assertEquals(SkillResult.NO_VALID_TARGET,
                engine.useSkill(state, "TORNADO", new SkillContext(p1, source, destination)));
        assertEquals(before, state);
    }

    @Test
    void tornadoRejectsOccupiedOrMissingDestination() {
        GameState state = state("X---XO----------");
        GameState before = copy(state);

        assertEquals(SkillResult.NO_VALID_TARGET,
                engine.useSkill(state, "TORNADO", new SkillContext(p1, 5, 4)));
        assertEquals(SkillResult.NO_VALID_TARGET,
                engine.useSkill(state, "TORNADO", new SkillContext(p1, 5)));
        assertEquals(before, state);
    }

    @ParameterizedTest
    @CsvSource({"0.0, 1", "0.25, 4", "0.5, 9", "0.9999999999999999, 15"})
    void blackHoleChoosesOnlyEmptyPositionsUsingTheInjectedDraw(double draw, int destination) {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextDouble()).thenReturn(draw);
        SkillContext context = SkillContext.randomized(p1, 5, random);
        GameState state = state("X----O----------");

        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "BURACO_NEGRO", context));

        assertMovedTo(state, 5, destination);
        verify(random, times(1)).nextDouble();
        verifyNoMoreInteractions(random);
    }

    @Test
    void blackHoleUsesTheOnlyEmptyPosition() {
        GameState state = state("XOOOOOOOOOOOOOO-");

        assertEquals(SkillResult.APPLIED,
                engine.useSkill(state, "BURACO_NEGRO", context("BURACO_NEGRO", 5, null)));

        assertEquals('-', state.getBoard().charAt(5));
        assertEquals('O', state.getBoard().charAt(15));
        assertEquals('X', state.getBoard().charAt(0));
    }

    @Test
    void blackHoleWithoutEmptyPositionHasNoEffect() {
        GameState state = state("XOOOOOOOOOOOOOOO");
        GameState before = copy(state);

        assertEquals(SkillResult.NO_VALID_TARGET,
                engine.useSkill(state, "BURACO_NEGRO", context("BURACO_NEGRO", 5, null)));
        assertEquals(before, state);
    }

    @Test
    void blackHoleRequiresTheServerDrawBeforeChangingState() {
        GameState state = state("X----O----------");
        GameState before = copy(state);

        assertThrows(IllegalArgumentException.class,
                () -> engine.useSkill(state, "BURACO_NEGRO", new SkillContext(p1, 5)));
        assertEquals(before, state);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidRandomValues(double draw) {
        assertThrows(IllegalArgumentException.class, () -> new SkillContext(p1, 5, null, draw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOMBA", "TORNADO", "BURACO_NEGRO", "CONGELAR"})
    void rejectsEmptyOwnAndOutOfBoardTargetsWithoutChangingState(String skill) {
        GameState state = state("X----O----------");
        GameState before = copy(state);

        for (Integer target : new Integer[]{null, -1, 16, 0, 2}) {
            assertEquals(SkillResult.NO_VALID_TARGET,
                    engine.useSkill(state, skill, context(skill, target, 6)));
            assertEquals(before, state);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOMBA", "TORNADO", "BURACO_NEGRO"})
    void reinforcedTargetRejectsRemovalAndMovement(String skill) {
        GameState state = state("X----O----------");
        state.getReinforcedPositions().put(5, 2);
        GameState before = copy(state);

        assertEquals(SkillResult.NO_VALID_TARGET, engine.useSkill(state, skill, context(skill, 5, 6)));
        assertEquals(before, state);

        assertTrue(engine.endAction(state, p1).isEmpty());
        assertEquals(1, state.getReinforcedPositions().get(5));
    }

    @Test
    void freezeIgnoresReinforcementAndExpiresAfterTwoEndActions() {
        GameState state = state("OOOOX-----------");
        state.getReinforcedPositions().put(0, 2);

        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "CONGELAR", new SkillContext(p1, 0)));
        assertEquals("OOOOX-----------", state.getBoard());
        assertEquals(2, state.getFrozenPositions().get(0));
        assertTrue(validator.resolveWinner(state.getBoard(), state.frozenSet(), 'X').isEmpty());

        assertTrue(engine.endAction(state, p1).isEmpty());
        assertEquals(1, state.getFrozenPositions().get(0));
        assertTrue(engine.endAction(state, p2).isEmpty());
        assertTrue(state.getFrozenPositions().isEmpty());
        assertEquals(Optional.of('O'), validator.resolveWinner(state.getBoard(), state.frozenSet(), 'X'));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TORNADO", "BURACO_NEGRO"})
    void movementKeepsFreezeOnTheMovedPiece(String skill) {
        GameState state = state("X----O----------");
        state.getFrozenPositions().put(5, 2);

        assertEquals(SkillResult.APPLIED,
                engine.useSkill(state, skill, new SkillContext(p1, 5, 6, 0.3)));

        assertMovedTo(state, 5, 6);
        assertEquals(Map.of(6, 2), state.getFrozenPositions());
    }

    @Test
    void bombRemovesFreezeWithThePiece() {
        GameState state = state("X----O-O--------");
        state.getFrozenPositions().put(5, 2);
        state.getFrozenPositions().put(7, 1);

        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "BOMBA", new SkillContext(p1, 5)));

        assertEquals(Map.of(7, 1), state.getFrozenPositions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOMBA", "TORNADO", "BURACO_NEGRO", "CONGELAR"})
    void effectsSurviveGameStateJsonRoundTrip(String skill) throws Exception {
        GameState state = state("X----O----------");
        state.getFrozenPositions().put(5, 1);

        assertEquals(SkillResult.APPLIED, engine.useSkill(state, skill, context(skill, 5, 6)));

        GameState restored = mapper.readValue(mapper.writeValueAsString(state), GameState.class);
        assertEquals(state, restored);
    }

    @Test
    @SuppressWarnings("unchecked")
    void blackHoleReusesTheDrawButRechecksEmptyCellsOnRedisRetry() throws Exception {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextDouble()).thenReturn(0.0);
        SkillContext context = SkillContext.randomized(p1, 5, random);
        GameState firstRead = state("X----O----------");
        firstRead.setVersion(2);
        firstRead.getFrozenPositions().put(5, 2);
        GameState secondRead = copy(firstRead);
        secondRead.setBoard("XX---O----------");
        secondRead.setVersion(3);

        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String key = "game:state:" + gameId;
        when(values.get(key)).thenReturn(mapper.writeValueAsString(firstRead), mapper.writeValueAsString(secondRead));
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString(), anyString(), anyString()))
                .thenReturn(false, true);
        GameStateManager manager = new GameStateManager(redis, mapper, 1800000, 5, mock(SecureRandom.class));

        manager.update(gameId, current -> {
            assertEquals(SkillResult.APPLIED, engine.useSkill(current, "BURACO_NEGRO", context));
            engine.endAction(current, p1);
            return current;
        });

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).execute(any(RedisScript.class), eq(List.of(key)),
                anyString(), json.capture(), eq("1800"));
        GameState firstAttempt = mapper.readValue(json.getAllValues().getFirst(), GameState.class);
        GameState stored = mapper.readValue(json.getAllValues().getLast(), GameState.class);
        assertMovedTo(firstAttempt, 5, 1);
        assertEquals('X', stored.getBoard().charAt(1));
        assertMovedTo(stored, 5, 2);
        assertEquals(4, stored.getVersion());
        assertEquals(Map.of(2, 1), stored.getFrozenPositions());
        verify(random, times(1)).nextDouble();
        verifyNoMoreInteractions(random);
    }

    @Test
    void oldAndExtendedSkillContextsCanBeReadFromJson() throws Exception {
        SkillContext oldContext = mapper.readValue(
                "{\"casterId\":\"" + p1 + "\",\"targetPosition\":5}", SkillContext.class);
        assertEquals(new SkillContext(p1, 5), oldContext);
        SkillContext context = new SkillContext(p1, 5, 6, 0.25);
        assertEquals(context, mapper.readValue(mapper.writeValueAsString(context), SkillContext.class));
    }

    private GameState state(String board) {
        return GameState.builder().gameId(gameId).board(board)
                .player1Id(p1).player2Id(p2).firstPlayerId(p1).currentTurnPlayerId(p1)
                .roundNumber(1).turnDurationMillis(10000).turnDeadlineEpochMillis(100000).build();
    }

    private SkillContext context(String skill, Integer target, Integer destination) {
        return new SkillContext(p1, target, destination, skill.equals("BURACO_NEGRO") ? 0.0 : null);
    }

    private GameState copy(GameState state) {
        return mapper.convertValue(state, GameState.class);
    }

    private void assertMovedTo(GameState state, int source, int destination) {
        assertEquals('-', state.getBoard().charAt(source));
        assertEquals('O', state.getBoard().charAt(destination));
        assertEquals('X', state.getBoard().charAt(0));
        assertEquals(1, state.getBoard().chars().filter(symbol -> symbol == 'O').count());
    }
}
