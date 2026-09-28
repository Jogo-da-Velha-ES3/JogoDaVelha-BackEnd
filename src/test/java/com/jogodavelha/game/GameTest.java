package com.jogodavelha.game;

import com.jogodavelha.auth.User;
import com.jogodavelha.room.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    private Room room;
    private User player1;
    private User player2;
    private Game game;

    @BeforeEach
    void setUp() {
        player1 = new User("player1", "pass", "p1@test.com");
        player1.setId(UUID.randomUUID());
        player2 = new User("player2", "pass", "p2@test.com");
        player2.setId(UUID.randomUUID());
        room = new Room("1234", player1);
        room.setId(UUID.randomUUID());
        game = new Game(room, player1);
        game.setPlayer2(player2);
    }

    @Test
    void testInitialState() {
        assertEquals(1, game.getCurrentRound());
        assertEquals(new Score(0, 0), game.getScore());
        assertEquals(Game.EMPTY_BOARD, game.getBoard());
        assertFalse(game.isSuddenDeath());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertNull(game.getWinner());
        assertFalse(game.isRewardsGranted());
    }

    @Test
    void testSuddenDeathFalseInRounds1And2() {
        // Round 1
        assertFalse(game.isSuddenDeath());

        // Avança para round 2
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(2, game.getCurrentRound());
        assertFalse(game.isSuddenDeath());

        // Avança para round 3
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(3, game.getCurrentRound());
        assertTrue(game.isSuddenDeath());
    }

    @Test
    void testRound1WinRound2WinPlayer1Wins() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        assertEquals(2, game.getCurrentRound());
        assertEquals(new Score(1, 0), game.getScore());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());

        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(player1, game.getWinner());
        assertEquals(new Score(2, 0), game.getScore());
    }

    @Test
    void testRound1DrawRound2WinPlayer1Wins() {
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(2, game.getCurrentRound());
        assertEquals(new Score(0, 0), game.getScore());

        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(player1, game.getWinner());
        assertEquals(new Score(1, 0), game.getScore());
    }

    @Test
    void testRound1WinRound2DrawPlayer1Wins() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        assertEquals(2, game.getCurrentRound());
        assertEquals(new Score(1, 0), game.getScore());

        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(player1, game.getWinner());
        assertEquals(new Score(1, 0), game.getScore());
    }

    @Test
    void testRound1WinPlayer1Round2WinPlayer2GoesToRound3() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        assertEquals(2, game.getCurrentRound());
        assertEquals(new Score(1, 0), game.getScore());

        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        assertEquals(3, game.getCurrentRound());
        assertEquals(new Score(1, 1), game.getScore());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.isSuddenDeath());
    }

    @Test
    void testRound1DrawRound2DrawGoesToRound3() {
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(2, game.getCurrentRound());
        assertEquals(new Score(0, 0), game.getScore());

        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(3, game.getCurrentRound());
        assertEquals(new Score(0, 0), game.getScore());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.isSuddenDeath());
    }

    @Test
    void testRound3WinPlayer1ResultsIn2x1() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        assertEquals(3, game.getCurrentRound());
        assertEquals(new Score(1, 1), game.getScore());

        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(player1, game.getWinner());
        assertEquals(new Score(2, 1), game.getScore());
    }

    @Test
    void testRound3DrawFrom0x0Keeps0x0() {
        game.registerRoundResult(RoundResult.DRAW);
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(3, game.getCurrentRound());
        assertEquals(new Score(0, 0), game.getScore());

        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertNull(game.getWinner());
        assertEquals(new Score(0, 0), game.getScore());
    }

    @Test
    void testRound3DrawFrom1x1Keeps1x1() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        assertEquals(3, game.getCurrentRound());
        assertEquals(new Score(1, 1), game.getScore());

        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertNull(game.getWinner());
        assertEquals(new Score(1, 1), game.getScore());
    }

    @Test
    void testRegisterRoundResultWithNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            game.registerRoundResult(null);
        });
    }

    @Test
    void testRegisterRoundResultWithoutPlayer2ThrowsException() {
        Game gameWithoutP2 = new Game(room, player1);
        assertThrows(IllegalStateException.class, () -> {
            gameWithoutP2.registerRoundResult(RoundResult.PLAYER1_WIN);
        });
        // Verifica que o estado não foi alterado
        assertEquals(1, gameWithoutP2.getCurrentRound());
        assertEquals(new Score(0, 0), gameWithoutP2.getScore());
        assertEquals(GameStatus.IN_PROGRESS, gameWithoutP2.getStatus());
    }

    @Test
    void testRegisterRoundResultInFinishedGameThrowsException() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER1_WIN);

        assertThrows(IllegalStateException.class, () -> {
            game.registerRoundResult(RoundResult.PLAYER1_WIN);
        });
    }

    @Test
    void testBoardClearedOnRoundAdvance() {
        game.setBoard("XXXX----OOOO----");
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(Game.EMPTY_BOARD, game.getBoard());
    }

    @Test
    void testGetOutcomeBeforeFinishedThrowsException() {
        assertThrows(IllegalStateException.class, game::getOutcome);
    }

    @Test
    void testGetOutcomeReturnsPlayer1Win() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER1_WIN);

        assertEquals(GameOutcome.PLAYER1_WIN, game.getOutcome());
    }

    @Test
    void testGetOutcomeReturnsPlayer2Win() {
        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        game.registerRoundResult(RoundResult.PLAYER2_WIN);

        assertEquals(GameOutcome.PLAYER2_WIN, game.getOutcome());
    }

    @Test
    void testGetOutcomeReturnsDraw() {
        game.registerRoundResult(RoundResult.DRAW);
        game.registerRoundResult(RoundResult.DRAW);
        game.registerRoundResult(RoundResult.DRAW);

        assertEquals(GameOutcome.DRAW, game.getOutcome());
    }

    @Test
    void testMarkRewardsGrantedBeforeFinishedThrowsException() {
        assertThrows(IllegalStateException.class, game::markRewardsGranted);
    }

    @Test
    void testMarkRewardsGrantedAfterFinished() {
        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER1_WIN);

        game.markRewardsGranted();
        assertTrue(game.isRewardsGranted());
    }

    @Test
    void testConstants() {
        assertEquals(5, Game.SUDDEN_DEATH_TURN_SECONDS);
        assertEquals(3, Game.MAX_ROUNDS);
        assertEquals("----------------", Game.EMPTY_BOARD);
    }
}
