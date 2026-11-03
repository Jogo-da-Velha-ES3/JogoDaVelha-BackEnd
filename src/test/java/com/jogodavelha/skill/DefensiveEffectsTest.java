package com.jogodavelha.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameValidator;
import com.jogodavelha.game.logic.MoveValidationException;
import com.jogodavelha.game.logic.MoveValidator;
import com.jogodavelha.game.logic.SkillValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jogodavelha.game.logic.SkillValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

class DefensiveEffectsTest {
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SkillEngine engine = new SkillEngine(List.of(
            new BombEffect(), new TornadoEffect(), new BlackHoleEffect(), new FreezeEffect(),
            new ShieldEffect(), new ReinforcementEffect(), new ForceFieldEffect(),
            new HourglassEffect(), new SilenceEffect(), new QuickSwapEffect()), new GameValidator());
    private final MoveValidator moves = new MoveValidator(
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC));

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shieldBlocksOnlyTheOpponentAndDoesNotPlaceAPiece(boolean player1Acts) {
        GameState state = state();
        UUID caster = player1Acts ? p1 : p2;
        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "ESCUDO", new SkillContext(caster, 3)));
        assertEquals('-', state.getBoard().charAt(3));
        assertEquals(new GameState.ShieldEffect(caster, 2), state.getShieldedPositions().get(3));

        state.setCurrentTurnPlayerId(caster);
        assertDoesNotThrow(() -> moves.validate(state, caster, 3, state.blockedFor(caster)));
        UUID opponent = state.getOpponentId(caster);
        state.setCurrentTurnPlayerId(opponent);
        var error = assertThrows(MoveValidationException.class,
                () -> moves.validate(state, opponent, 3, state.blockedFor(opponent)));
        assertEquals(MoveValidationException.Code.POSITION_BLOCKED, error.getCode());
    }

    @Test
    void shieldRejectsOccupiedOutOfBoardAndOpponentBlockedTargets() {
        GameState state = state();
        state.getShieldedPositions().put(1, new GameState.ShieldEffect(p2, 2));
        GameState before = copy(state);
        for (Integer position : new Integer[]{null, -1, 16, 0, 5, 1}) {
            var error = assertThrows(SkillValidationException.class,
                    () -> engine.useSkill(state, "ESCUDO", new SkillContext(p1, position)));
            assertEquals(SKILL_INVALID_TARGET, error.getCode());
            assertEquals(before, state);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOMBA", "TORNADO", "BURACO_NEGRO", "CONGELAR"})
    void reinforcementStopsRemovalAndMovementButNotFreeze(String code) {
        GameState state = state();
        engine.useSkill(state, "REFORCO", new SkillContext(p2, 5));
        engine.endAction(state, p2);
        GameState before = copy(state);

        var result = engine.useSkill(state, code, context(code));

        if (code.equals("CONGELAR")) {
            assertEquals(SkillResult.APPLIED, result);
            assertEquals(Map.of(5, 2), state.getFrozenPositions());
            assertEquals(before.getBoard(), state.getBoard());
        } else {
            assertEquals(SkillResult.NO_VALID_TARGET, result);
            assertEquals(before, state);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOMBA", "TORNADO", "BURACO_NEGRO", "CONGELAR", "AMPULHETA", "SILENCIO"})
    void forceFieldCancelsOnlyTheFirstOffensiveWithoutReflection(String code) {
        GameState state = state();
        engine.useSkill(state, "CAMPO_DE_FORCA", new SkillContext(p2, null));
        engine.useSkill(state, "CAMPO_DE_FORCA", new SkillContext(p1, null));
        engine.endAction(state, p2);
        GameState expected = copy(state);
        expected.getForceFieldTurns().remove(p2);

        assertEquals(SkillResult.BLOCKED_BY_FORCE_FIELD, engine.useSkill(state, code, context(code)));
        assertEquals(expected, state);
        assertEquals(SkillResult.APPLIED, engine.useSkill(state, code, context(code)));
        assertEquals(1, state.getForceFieldTurns().get(p1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESCUDO", "REFORCO", "CAMPO_DE_FORCA", "TROCA_RAPIDA"})
    void defensiveSkillsDoNotConsumeTheOpponentsForceField(String code) {
        GameState state = state();
        engine.useSkill(state, "CAMPO_DE_FORCA", new SkillContext(p2, null));

        assertEquals(SkillResult.APPLIED, engine.useSkill(state, code, context(code)));
        assertEquals(2, state.getForceFieldTurns().get(p2));
    }

    @Test
    void invalidRequestDoesNotConsumeForceFieldEvenWithReinforcement() {
        GameState state = state();
        engine.useSkill(state, "REFORCO", new SkillContext(p2, 5));
        engine.useSkill(state, "CAMPO_DE_FORCA", new SkillContext(p2, null));
        GameState before = copy(state);

        var target = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, "BOMBA", new SkillContext(p1, 0)));
        var destination = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, "TORNADO", new SkillContext(p1, 5, 10)));
        assertEquals(SKILL_INVALID_TARGET, target.getCode());
        assertEquals(SKILL_INVALID_DESTINATION, destination.getCode());
        assertEquals(before, state);

        assertEquals(SkillResult.BLOCKED_BY_FORCE_FIELD,
                engine.useSkill(state, "BOMBA", context("BOMBA")));
        assertEquals(2, state.getReinforcedPositions().get(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOMBA", "ESCUDO", "TORNADO", "BURACO_NEGRO", "AMPULHETA", "CONGELAR",
            "CAMPO_DE_FORCA", "TROCA_RAPIDA", "REFORCO", "SILENCIO"})
    void silenceRejectsEverySkillWithoutChangingStateOrConsumingForceField(String code) {
        GameState state = state();
        state.getSilencedTurns().put(p1, 1);
        state.getForceFieldTurns().put(p2, 1);
        GameState before = copy(state);

        var error = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, code, context(code)));

        assertEquals(SKILL_SILENCED, error.getCode());
        assertEquals(before, state);
    }

    @Test
    void silenceAllowsANormalMoveAndExpiresAfterTheAffectedTurn() {
        GameState state = state();
        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "SILENCIO", new SkillContext(p1, null)));
        assertEquals(2, state.getSilencedTurns().get(p2));
        engine.endAction(state, p1);
        state.setCurrentTurnPlayerId(p2);
        assertEquals(1, state.getSilencedTurns().get(p2));
        assertDoesNotThrow(() -> moves.validate(state, p2, 1, state.blockedFor(p2)));
        state.setBoard("XO---O----------");
        engine.endAction(state, p2);
        assertTrue(state.getSilencedTurns().isEmpty());
        assertEquals(SkillResult.APPLIED, engine.useSkill(state, "REFORCO", new SkillContext(p2, 5)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 6, 9})
    void quickSwapMovesOwnPieceAndKeepsItsEffects(int destination) {
        GameState state = state();
        state.setBoard("O----X----------");
        state.getFrozenPositions().put(5, 1);
        state.getReinforcedPositions().put(5, 2);

        assertEquals(SkillResult.APPLIED,
                engine.useSkill(state, "TROCA_RAPIDA", new SkillContext(p1, 5, destination)));

        assertEquals('-', state.getBoard().charAt(5));
        assertEquals('X', state.getBoard().charAt(destination));
        assertEquals('O', state.getBoard().charAt(0));
        assertEquals(Map.of(destination, 1), state.getFrozenPositions());
        assertEquals(Map.of(destination, 2), state.getReinforcedPositions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REFORCO", "TROCA_RAPIDA"})
    void ownPieceSkillsRejectEmptyEnemyAndOutOfBoardTargets(String code) {
        GameState state = state();
        GameState before = copy(state);
        for (Integer target : new Integer[]{null, -1, 16, 5, 2}) {
            var error = assertThrows(SkillValidationException.class,
                    () -> engine.useSkill(state, code, new SkillContext(p1, target, 1)));
            assertEquals(SKILL_INVALID_TARGET, error.getCode());
            assertEquals(before, state);
        }
    }

    @ParameterizedTest
    @CsvSource({"3, 4", "4, 3", "5, 10", "5, 15", "5, 5", "5, -1", "5, 16"})
    void quickSwapRejectsDiagonalsRowWrappingAndInvalidDestinations(int source, int destination) {
        GameState state = state();
        char[] board = GameState.EMPTY_BOARD.toCharArray();
        board[source] = 'X';
        state.setBoard(new String(board));
        GameState before = copy(state);

        var error = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, "TROCA_RAPIDA", new SkillContext(p1, source, destination)));
        assertEquals(SKILL_INVALID_DESTINATION, error.getCode());
        assertEquals(before, state);
    }

    @Test
    void quickSwapRejectsOccupiedAndShieldedDestinationsAndDetectsNoAvailableNeighbor() {
        GameState state = state();
        state.setBoard("OX--OXO--O------");
        GameState before = copy(state);
        var occupied = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, "TROCA_RAPIDA", new SkillContext(p1, 5, 1)));
        assertEquals(SKILL_INVALID_DESTINATION, occupied.getCode());
        assertEquals(SkillResult.NO_VALID_TARGET,
                engine.useSkill(state, "TROCA_RAPIDA", new SkillContext(p1, 5)));
        assertEquals(before, state);

        state.setBoard("O----X----------");
        state.getShieldedPositions().put(6, new GameState.ShieldEffect(p2, 2));
        before = copy(state);
        var blocked = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, "TROCA_RAPIDA", new SkillContext(p1, 5, 6)));
        var missing = assertThrows(SkillValidationException.class,
                () -> engine.useSkill(state, "TROCA_RAPIDA", new SkillContext(p1, 5)));
        assertEquals(SKILL_INVALID_DESTINATION, blocked.getCode());
        assertEquals(SKILL_INVALID_DESTINATION, missing.getCode());
        assertEquals(before, state);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESCUDO", "REFORCO", "CAMPO_DE_FORCA", "AMPULHETA", "SILENCIO"})
    void countersStartAtTwoTickAtEachEndActionAndExpire(String code) {
        GameState state = state();
        engine.useSkill(state, code, context(code));
        assertCounter(state, code, 2);
        engine.endAction(state, p1);
        assertCounter(state, code, 1);
        engine.endAction(state, p2);
        assertCounter(state, code, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESCUDO", "REFORCO", "CAMPO_DE_FORCA", "AMPULHETA", "SILENCIO", "TROCA_RAPIDA"})
    void defensiveEffectsSurviveJsonRoundTripAndAreClearedOnNewRound(String code) throws Exception {
        GameState state = state();
        engine.useSkill(state, code, context(code));
        GameState restored = mapper.readValue(mapper.writeValueAsString(state), GameState.class);
        assertEquals(state, restored);

        restored.startRound(2, 10000, 1000);
        assertTrue(restored.getShieldedPositions().isEmpty());
        assertTrue(restored.getReinforcedPositions().isEmpty());
        assertTrue(restored.getForceFieldTurns().isEmpty());
        assertTrue(restored.getHourglassTurns().isEmpty());
        assertTrue(restored.getSilencedTurns().isEmpty());
    }

    private void assertCounter(GameState state, String code, int turns) {
        int actual = switch (code) {
            case "ESCUDO" -> state.getShieldedPositions().containsKey(1)
                    ? state.getShieldedPositions().get(1).turns() : 0;
            case "REFORCO" -> state.getReinforcedPositions().getOrDefault(0, 0);
            case "CAMPO_DE_FORCA" -> state.getForceFieldTurns().getOrDefault(p1, 0);
            case "AMPULHETA" -> state.getHourglassTurns().getOrDefault(p2, 0);
            case "SILENCIO" -> state.getSilencedTurns().getOrDefault(p2, 0);
            default -> throw new AssertionError(code);
        };
        assertEquals(turns, actual);
    }

    private SkillContext context(String code) {
        return switch (code) {
            case "ESCUDO" -> new SkillContext(p1, 1);
            case "REFORCO" -> new SkillContext(p1, 0);
            case "TROCA_RAPIDA" -> new SkillContext(p1, 0, 1);
            case "CAMPO_DE_FORCA", "AMPULHETA", "SILENCIO" -> new SkillContext(p1, null);
            default -> new SkillContext(p1, 5, 6, 0.0);
        };
    }

    private GameState state() {
        return GameState.builder().gameId(UUID.randomUUID()).board("X----O----------")
                .player1Id(p1).player2Id(p2).firstPlayerId(p1).currentTurnPlayerId(p1)
                .roundNumber(1).turnDurationMillis(10000).turnDeadlineEpochMillis(100000).build();
    }

    private GameState copy(GameState state) {
        return mapper.convertValue(state, GameState.class);
    }
}
