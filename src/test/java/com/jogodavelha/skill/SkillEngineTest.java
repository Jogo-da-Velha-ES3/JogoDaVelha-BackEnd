package com.jogodavelha.skill;

import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkillEngineTest {
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();

    /** Efeito de teste: congela a casa-alvo por 2 turnos. */
    private static class FakeFreeze implements SkillEffect {
        public String skillName() { return "fake-freeze"; }
        public boolean hasValidTarget(GameState s, SkillContext c) {
            return c.targetPosition() != null && s.getBoard().charAt(c.targetPosition()) != '-';
        }
        public void apply(GameState s, SkillContext c) { s.getFrozenPositions().put(c.targetPosition(), 2); }
    }

    private final SkillEngine engine = new SkillEngine(List.of(new FakeFreeze()), new GameValidator());

    private GameState state(String board) {
        GameState s = GameState.createInitial(UUID.randomUUID(), p1, p2, 10000, new java.security.SecureRandom());
        s.setBoard(board);
        return s;
    }

    @Test
    void appliesSkillThroughEffect() {
        GameState s = state("X---------------");
        assertEquals(SkillResult.APPLIED, engine.useSkill(s, "fake-freeze", new SkillContext(p1, 0)));
        assertEquals(2, s.getFrozenPositions().get(0));
    }

    @Test
    void skillWithoutValidTargetChangesNothing() {
        GameState s = state("----------------");
        assertEquals(SkillResult.NO_VALID_TARGET, engine.useSkill(s, "fake-freeze", new SkillContext(p1, 0)));
        assertTrue(s.getFrozenPositions().isEmpty());
    }

    @Test
    void unknownSkillIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.useSkill(state("----------------"), "nope", new SkillContext(p1, 0)));
    }

    @Test
    void countersDecrementAndExpireExactly() {
        GameState s = state("X---------------");
        s.getFrozenPositions().put(0, 2);
        s.getShieldedPositions().put(5, new GameState.ShieldEffect(p1, 1));

        engine.endAction(s, p1);
        assertEquals(1, s.getFrozenPositions().get(0));
        assertTrue(s.getShieldedPositions().isEmpty());

        engine.endAction(s, p2);
        assertTrue(s.getFrozenPositions().isEmpty());
    }

    @Test
    void winnerIsResolvedBeforeCountersExpire() {
        // linha 0-3 completa de X, com a casa 3 congelada por 1 turno
        GameState s = state("XXXX------------");
        s.getFrozenPositions().put(3, 1);

        assertEquals(Optional.empty(), engine.endAction(s, p1)); // congelada ainda vale na checagem
        assertTrue(s.getFrozenPositions().isEmpty());            // expirou depois
        assertEquals(Optional.of('X'), engine.endAction(s, p1)); // próxima checagem já vê a vitória
    }

    @Test
    void timeoutAlsoResolvesThenTicks() {
        GameState s = state("XXXX------------");
        s.getFrozenPositions().put(0, 1);
        assertEquals(Optional.empty(), engine.endAction(s, p2)); // timeout do P2
        assertTrue(s.getFrozenPositions().isEmpty());
    }

    @Test
    void shieldBlocksOnlyTheOpponent() {
        GameState s = state("----------------");
        s.getShieldedPositions().put(5, new GameState.ShieldEffect(p1, 2));
        assertTrue(s.blockedFor(p2).contains(5));
        assertFalse(s.blockedFor(p1).contains(5));
    }

    @Test
    void hourglassShortensOpponentTurnOnly() {
        GameState s = state("----------------");
        s.getHourglassTurns().put(p2, 2);
        assertEquals(5000, s.effectiveTurnDuration(p2, 10000));
        assertEquals(10000, s.effectiveTurnDuration(p1, 10000));
    }

    @Test
    void hourglassExpiresAfterOpponentTurn() {
        GameState s = state("----------------");
        s.getHourglassTurns().put(p2, 2);
        engine.endAction(s, p1);                                  // lançador: 2 -> 1
        assertEquals(5000, s.effectiveTurnDuration(p2, 10000));   // turno do oponente
        engine.endAction(s, p2);                                  // 1 -> 0
        assertEquals(10000, s.effectiveTurnDuration(p2, 10000));
    }

    @Test
    void startRoundClearsEffectsButKeepsTimeouts() {
        GameState s = state("XX--------------");
        s.getFrozenPositions().put(0, 2);
        s.getShieldedPositions().put(5, new GameState.ShieldEffect(p1, 2));
        s.getHourglassTurns().put(p2, 2);
        s.recordTimeout(p1);

        s.startRound(2, 10000, System.currentTimeMillis());

        assertTrue(s.getFrozenPositions().isEmpty());
        assertTrue(s.getShieldedPositions().isEmpty());
        assertTrue(s.getHourglassTurns().isEmpty());
        assertEquals(1, s.getConsecutiveTimeouts(p1));
    }
}