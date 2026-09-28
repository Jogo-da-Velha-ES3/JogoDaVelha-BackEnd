package com.jogodavelha.game;

import com.jogodavelha.auth.User;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomStatus;
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
        player1.setId(UUID.randomUUID().toString());
        player2 = new User("player2", "pass", "p2@test.com");
        player2.setId(UUID.randomUUID().toString());
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
    void testWinnerGets50CoinsLoserGets10() {
        int initialP1 = player1.getCoinsBalance();
        int initialP2 = player2.getCoinsBalance();

        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER1_WIN);

        assertEquals(initialP1 + Game.COINS_WIN, player1.getCoinsBalance());
        assertEquals(initialP2 + Game.COINS_LOSS, player2.getCoinsBalance());
    }

    @Test
    void testPlayer2WinsGets50CoinsPlayer1Gets10() {
        int initialP1 = player1.getCoinsBalance();
        int initialP2 = player2.getCoinsBalance();

        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        game.registerRoundResult(RoundResult.PLAYER2_WIN);

        assertEquals(initialP1 + Game.COINS_LOSS, player1.getCoinsBalance());
        assertEquals(initialP2 + Game.COINS_WIN, player2.getCoinsBalance());
    }

    @Test
    void testDrawEachPlayerGets20Coins() {
        int initialP1 = player1.getCoinsBalance();
        int initialP2 = player2.getCoinsBalance();

        game.registerRoundResult(RoundResult.DRAW);
        game.registerRoundResult(RoundResult.DRAW);
        game.registerRoundResult(RoundResult.DRAW);

        assertEquals(initialP1 + Game.COINS_DRAW, player1.getCoinsBalance());
        assertEquals(initialP2 + Game.COINS_DRAW, player2.getCoinsBalance());
    }

    @Test
    void testDrawFrom1x1EachPlayerGets20Coins() {
        int initialP1 = player1.getCoinsBalance();
        int initialP2 = player2.getCoinsBalance();

        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER2_WIN);
        game.registerRoundResult(RoundResult.DRAW);

        assertEquals(initialP1 + Game.COINS_DRAW, player1.getCoinsBalance());
        assertEquals(initialP2 + Game.COINS_DRAW, player2.getCoinsBalance());
    }

    @Test
    void testCallingFinishTwiceDoesNotPayTwice() {
        int initialP1 = player1.getCoinsBalance();
        int initialP2 = player2.getCoinsBalance();

        game.registerRoundResult(RoundResult.PLAYER1_WIN);
        game.registerRoundResult(RoundResult.PLAYER1_WIN);

        game.finish(); // Segunda chamada (já foi chamado no registerRoundResult)

        assertEquals(initialP1 + Game.COINS_WIN, player1.getCoinsBalance());
        assertEquals(initialP2 + Game.COINS_LOSS, player2.getCoinsBalance());
    }

    @Test
    void testNoCoinsPaidWhileGameInProgress() {
        int initialP1 = player1.getCoinsBalance();
        int initialP2 = player2.getCoinsBalance();

        game.registerRoundResult(RoundResult.PLAYER1_WIN);

        assertEquals(initialP1, player1.getCoinsBalance());
        assertEquals(initialP2, player2.getCoinsBalance());
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
    void testFinishWithoutPlayer2ThrowsException() {
        Game gameWithoutP2 = new Game(room, player1);
        assertThrows(IllegalStateException.class, gameWithoutP2::finish);
    }

    @Test
    void testBoardClearedOnRoundAdvance() {
        game.setBoard("XXXX----OOOO----");
        game.registerRoundResult(RoundResult.DRAW);
        assertEquals(Game.EMPTY_BOARD, game.getBoard());
    }

    @Test
    void testConstants() {
        assertEquals(5, Game.SUDDEN_DEATH_TURN_SECONDS);
        assertEquals(50, Game.COINS_WIN);
        assertEquals(20, Game.COINS_DRAW);
        assertEquals(10, Game.COINS_LOSS);
        assertEquals(3, Game.MAX_ROUNDS);
        assertEquals("----------------", Game.EMPTY_BOARD);
    }
}
